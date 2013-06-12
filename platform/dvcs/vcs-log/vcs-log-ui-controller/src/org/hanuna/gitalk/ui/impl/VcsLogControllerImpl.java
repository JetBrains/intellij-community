package org.hanuna.gitalk.ui.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.data.DataPackUtils;
import org.hanuna.gitalk.data.impl.DataLoaderImpl;
import org.hanuna.gitalk.data.impl.FakeCommitsInfo;
import org.hanuna.gitalk.data.rebase.InteractiveRebaseBuilder;
import org.hanuna.gitalk.data.rebase.VcsLogActionHandler;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graphmodel.FragmentManager;
import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.hanuna.gitalk.log.commit.parents.FakeCommitParents;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.ui.DragDropListener;
import org.hanuna.gitalk.ui.VcsLogController;
import org.hanuna.gitalk.ui.VcsLogUI;
import org.hanuna.gitalk.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.util.*;

/**
 * @author erokhins
 */
public class VcsLogControllerImpl implements VcsLogController {

  private static final Logger LOG = Logger.getInstance(VcsLogController.class);
  private volatile DataLoaderImpl dataLoader;
  private final Project myProject;
  private final BackgroundTaskQueue myDataLoaderQueue;

  private DataPack dataPack;
  private DataPackUtils dataPackUtils;

  private GraphTableModel graphTableModel;
  private VcsLogProvider myLogProvider;
  @NotNull private final VirtualFile myRoot;

  private GraphElement prevGraphElement = null;

  private DragDropListener dragDropListener = DragDropListener.EMPTY;
  private VcsLogActionHandler myVcsLogActionHandler = VcsLogActionHandler.DO_NOTHING;
  private final VcsLogActionHandler.Callback myCallback = new Callback();

  private final MyInteractiveRebaseBuilder rebaseDelegate = new MyInteractiveRebaseBuilder();
  private InteractiveRebaseBuilder myInteractiveRebaseBuilder = new InteractiveRebaseBuilder() {

    @Override
    public void startRebase(Ref subjectRef, Node onto) {
      rebaseDelegate.startRebase(subjectRef, onto);
      init();
    }

    @Override
    public void startRebaseOnto(Ref subjectRef, Node base, List<Node> nodesToRebase) {
      rebaseDelegate.startRebaseOnto(subjectRef, base, nodesToRebase);
      init();
    }

    @Override
    public void moveCommits(Ref subjectRef, Node base, InsertPosition position, List<Node> nodesToInsert) {
      rebaseDelegate.moveCommits(subjectRef, base, position, nodesToInsert);
      init();
    }

    @Override
    public void fixUp(Ref subjectRef, Node target, List<Node> nodesToFixUp) {
      rebaseDelegate.fixUp(subjectRef, target, nodesToFixUp);
      init();
    }

    @Override
    public void reword(Ref subjectRef, Node commitToReword, String newMessage) {
      rebaseDelegate.reword(subjectRef, commitToReword, newMessage);
      init();
    }

    @Override
    public List<RebaseCommand> getRebaseCommands() {
      return super.getRebaseCommands();
    }
  };

  private final CacheGet<Hash, VcsCommit> commitDataCache = new CacheGet<Hash, VcsCommit>(new Function<Hash, VcsCommit>() {
    @NotNull
    @Override
    public VcsCommit fun(@NotNull Hash key) {
      // TODO
      try {
        return myLogProvider.readCommitsData(myRoot, Collections.singletonList(key.toStrHash())).get(0);
      }
      catch (VcsException e) {
        throw new RuntimeException(e);
      }
    }
  }, 5000);
  private final VcsLogUI mySwingUi;

  public VcsLogControllerImpl(@NotNull Project project, @NotNull VcsLogProvider logProvider, @NotNull VirtualFile root) {
    myProject = project;
    myLogProvider = logProvider;
    myRoot = root;
    myDataLoaderQueue = new BackgroundTaskQueue(myProject, "Loading history...");
    mySwingUi = new VcsLogUI(this);

  }

  private void dataInit() {
    dataPack = dataLoader.getDataPack();
    graphTableModel = new GraphTableModel(dataPack);
    dataPackUtils = new DataPackUtils(dataPack);
  }

  public void init() {
    myDataLoaderQueue.run(new Task.Backgroundable(myProject, "Loading history...", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        dataLoader = new DataLoaderImpl(VcsLogControllerImpl.this.myProject, commitDataCache, myLogProvider);

        try {
          dataLoader.readNextPart(indicator, myRoot);
          dataInit();
        }
        catch (VcsException e) {
          notifyError(e);
        }

        ((GraphTableModel) getGraphTableModel()).addReworded(rebaseDelegate.reworded);
        ((GraphTableModel) getGraphTableModel()).addFixedUp(rebaseDelegate.fixedUp);

        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            mySwingUi.loadingCompleted();
            mySwingUi.updateUI();
            for (Hash hash : rebaseDelegate.selected) {
              mySwingUi.addToSelection(hash);
            }
          }
        });
      }
    });

  }

  private void notifyError(VcsException e) {
    LOG.warn(e);
    VcsBalloonProblemNotifier.showOverChangesView(myProject, e.getMessage(), MessageType.ERROR);
  }

  // TODO is null before initial load
  @Override
  @NotNull
  public TableModel getGraphTableModel() {
    return graphTableModel;
  }

  @Override
  public void over(@Nullable GraphElement graphElement) {
    SelectController selectController = dataPack.getPrintCellModel().getSelectController();
    FragmentManager fragmentManager = dataPack.getGraphModel().getFragmentManager();
    if (graphElement == prevGraphElement) {
      return;
    }
    else {
      prevGraphElement = graphElement;
    }
    selectController.deselectAll();
    if (graphElement == null) {
      mySwingUi.updateUI();
    }
    else {
      GraphFragment graphFragment = fragmentManager.relateFragment(graphElement);
      selectController.select(graphFragment);
      mySwingUi.updateUI();
    }
  }

  public void click(@Nullable GraphElement graphElement) {
    SelectController selectController = dataPack.getPrintCellModel().getSelectController();
    FragmentManager fragmentController = dataPack.getGraphModel().getFragmentManager();
    selectController.deselectAll();
    if (graphElement == null) {
      return;
    }
    GraphFragment fragment = fragmentController.relateFragment(graphElement);
    if (fragment == null) {
      return;
    }
    UpdateRequest updateRequest = fragmentController.changeVisibility(fragment);
    mySwingUi.updateUI();
    mySwingUi.jumpToRow(updateRequest.from());
  }

  public void click(int rowIndex) {
    dataPack.getPrintCellModel().getCommitSelectController().deselectAll();
    Node node = dataPackUtils.getNode(rowIndex);
    if (node != null) {
      FragmentManager fragmentController = dataPack.getGraphModel().getFragmentManager();
      dataPack.getPrintCellModel().getCommitSelectController().select(fragmentController.allCommitsCurrentBranch(node));
    }
    mySwingUi.updateUI();

  }

  @Override
  public void doubleClick(int rowIndex) {
    if (rowIndex == graphTableModel.getRowCount() - 1) {
      readNextPart();
    }
  }


  @Override
  public void readNextPart() {
    myDataLoaderQueue.run(new Task.Backgroundable(myProject, "Loading history...", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          dataLoader.readNextPart(indicator, myRoot);
          UIUtil.invokeAndWaitIfNeeded(new Runnable() {
            @Override
            public void run() {
              mySwingUi.updateUI();
            }
          });

        }
        catch (VcsException e) {
          notifyError(e);
        }
      }
    });
  }

  @Override
  public void showAll() {
    dataPack.getGraphModel().getFragmentManager().showAll();
    mySwingUi.updateUI();
    mySwingUi.jumpToRow(0);
  }

  @Override
  public void hideAll() {
    new Task.Backgroundable(myProject, "Hiding long branches...", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            MyTimer timer = new MyTimer("hide All");
            dataPack.getGraphModel().getFragmentManager().hideAll();

            mySwingUi.updateUI();
            //TODO:
            mySwingUi.jumpToRow(0);
            timer.print();
          }
        });
      }
    }.queue();
  }

  @Override
  public void setLongEdgeVisibility(boolean visibility) {
    dataPack.getPrintCellModel().setLongEdgeVisibility(visibility);
    mySwingUi.updateUI();
  }

  @Override
  public boolean areLongEdgesHidden() {
    return dataPack == null ? GraphPrintCellModel.HIDE_LONG_EDGES_DEFAULT : dataPack.getPrintCellModel().areLongEdgesHidden();
  }

  @NotNull
  @Override
  public JComponent getMainComponent() {
    return mySwingUi.getMainFrame().getMainComponent();
  }

  @Override
  public void jumpToCommit(Hash commitHash) {
    int row = dataPackUtils.getRowByHash(commitHash);
    if (row != -1) {
      mySwingUi.jumpToRow(row);
    }
  }

  @Override
  public Collection<Ref> getRefs() {
    return dataPack == null ? Collections.<Ref>emptyList() : dataPack.getRefsModel().getAllRefs();
  }

  @NotNull
  @Override
  public DragDropListener getDragDropListener() {
    return dragDropListener;
  }

  public void setDragDropListener(@NotNull DragDropListener dragDropListener) {
    this.dragDropListener = dragDropListener;
  }

  @NotNull
  @Override
  public InteractiveRebaseBuilder getInteractiveRebaseBuilder() {
    return myInteractiveRebaseBuilder;
  }

  @NotNull
  @Override
  public VcsLogActionHandler getVcsLogActionHandler() {
    return myVcsLogActionHandler;
  }

  public void setVcsLogActionHandler(@NotNull VcsLogActionHandler vcsLogActionHandler) {
    myVcsLogActionHandler = vcsLogActionHandler;
  }

  @Override
  public DataPack getDataPack() {
    return dataPack;
  }

  @Override
  public DataPackUtils getDataPackUtils() {
    return dataPackUtils;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void applyInteractiveRebase() {
    if (rebaseDelegate.resultRef == null) {
      return;
    }
    getVcsLogActionHandler().interactiveRebase(rebaseDelegate.subjectRef, rebaseDelegate.branchBase, getCallback(),
                                            rebaseDelegate.getRebaseCommands());
    cancelInteractiveRebase();
  }

  @Override
  public void cancelInteractiveRebase() {
    rebaseDelegate.reset();
    init();
  }

  @Override
  public VcsLogActionHandler.Callback getCallback() {
    return myCallback;
  }

  @Override
  public boolean isInteractiveRebaseInProgress() {
    return rebaseDelegate.branchBase != null;
  }

  private class MyInteractiveRebaseBuilder extends InteractiveRebaseBuilder {

    private Node branchBase = null;
    private int insertAfter = -1;
    private List<FakeCommitParents> fakeBranch = new ArrayList<FakeCommitParents>();
    private Ref subjectRef = null;
    private Ref resultRef = null;
    private Map<Hash, String> reworded = new HashMap<Hash, String>();
    private Set<Hash> fixedUp = new HashSet<Hash>();
    private List<Hash> selected = new ArrayList<Hash>();

    private FakeCommitParents createFake(Hash oldHash, Hash newParent) {
      return new FakeCommitParents(newParent, new RebaseCommand(RebaseCommand.RebaseCommandKind.PICK, FakeCommitParents.getOriginal(oldHash)));
    }

    public void reset() {
      branchBase = null;
      insertAfter = -1;
      fakeBranch.clear();
      subjectRef = null;
      resultRef = null;
      reworded.clear();
      fixedUp.clear();
      selected.clear();
    }

    @Override
    public void startRebase(Ref subjectRef, Node onto) {
      List<Node> commitsToRebase =
        getDataPackUtils().getCommitsDownToCommon(onto, getDataPackUtils().getNodeByHash(subjectRef.getCommitHash()));
      startRebaseOnto(subjectRef, onto, commitsToRebase.subList(0, commitsToRebase.size() - 1));
    }

    private void setResultRef(Ref subjectRef) {
      if (resultRef == null) {
        this.subjectRef = subjectRef;
      }
      this.resultRef = new Ref(fakeBranch.get(0).getHash(), subjectRef.getName(), Ref.RefType.BRANCH_UNDER_INTERACTIVE_REBASE);
    }

    @Override
    public void startRebaseOnto(Ref subjectRef, Node base, List<Node> nodesToRebase) {
      reset();
      this.branchBase = base;
      this.insertAfter = base.getRowIndex();

      this.fakeBranch = createFakeCommits(base, nodesToRebase);

      selected.add(fakeBranch.get(0).getHash());

      setResultRef(subjectRef);
    }

    @Override
    public void moveCommits(Ref subjectRef, Node base, InsertPosition position, List<Node> nodesToInsert) {
      if (resultRef != null && resultRef != subjectRef) {
        reset();
      }
      DataPackUtils du = getDataPackUtils();
      if (position == InsertPosition.BELOW) {
        //insertAfter = base.getRowIndex() + 1;
        // TODO: what if many edges?
        base = getParent(base);
      }
      else {
        //insertAfter = base.getRowIndex();
      }
      Node lowestInserted = nodesToInsert.get(nodesToInsert.size() - 1);
      if (du.isAncestorOf(base, lowestInserted)) {
        this.branchBase = base;
      }
      else {
        // TODO: many parents?
        this.branchBase = getParent(lowestInserted);
      }

      if (!fakeBranch.isEmpty()) {
        FakeCommitParents lowestFakeCommit = fakeBranch.get(fakeBranch.size() - 1);
        Node lowestFakeNode = du.getNodeByHash(lowestFakeCommit.getHash());

        if (lowestFakeNode == branchBase || du.isAncestorOf(lowestFakeNode, branchBase)) {
          branchBase = getParent(lowestFakeNode);
        }
      }

      Set<Node> nodesToRemove = new HashSet<Node>(nodesToInsert);
      List<Node> branch = du.getCommitsInBranchAboveBase(this.branchBase, du.getNodeByHash(subjectRef.getCommitHash()));
      List<Node> result = new ArrayList<Node>();
      boolean baseFound = false;
      for (Node node : branch) {
        if (node == base) {
          result.addAll(nodesToInsert);
          baseFound = true;
        }
        if (!nodesToRemove.contains(node)) {
          result.add(node);
        }
      }
      if (!baseFound) {
        result.addAll(nodesToInsert);
      }

      this.fakeBranch = createFakeCommits(this.branchBase, result);

      int maxIndex = -1;
      for (Node node : result) {
        if (maxIndex < node.getRowIndex()) {
          maxIndex = node.getRowIndex();
        }
      }
      insertAfter = maxIndex + 1;

      selected.clear();
      for (Node node : nodesToInsert) {
        Hash fakeHash = FakeCommitParents.fakeHash(node.getCommitHash());
        selected.add(fakeHash);
      }

      setResultRef(subjectRef);
      dumpCommands();
    }

    @Override
    public void reword(Ref subjectRef, Node commitToReword, String newMessage) {
      moveCommits(subjectRef, commitToReword, InsertPosition.ABOVE, Collections.singletonList(commitToReword));
      reworded.put(FakeCommitParents.fakeHash(commitToReword.getCommitHash()), newMessage);
      dumpCommands();
    }

    @Override
    public void fixUp(Ref subjectRef, Node target, List<Node> nodesToFixUp) {
      moveCommits(subjectRef, target, InsertPosition.BELOW, nodesToFixUp);
      for (Node node : nodesToFixUp) {
        fixedUp.add(FakeCommitParents.fakeHash(node.getCommitHash()));
      }
      dumpCommands();
    }

    private void dumpCommands() {
      for (RebaseCommand command : getRebaseCommands()) {
        System.out.println(command);
      }
    }

    private List<FakeCommitParents> createFakeCommits(Node base, List<Node> nodesToRebase) {
      List<FakeCommitParents> result = new ArrayList<FakeCommitParents>();
      List<Node> reversed = reverse(nodesToRebase);
      Hash parent = base.getCommitHash();
      for (Node node : reversed) {
        FakeCommitParents fakeCommit = createFake(node.getCommitHash(), parent);
        parent = fakeCommit.getHash();
        result.add(fakeCommit);
      }
      Collections.reverse(result);
      return result;
    }

    private List<Node> reverse(List<Node> nodesToRebase) {
      List<Node> reversed = new ArrayList<Node>(nodesToRebase);
      Collections.reverse(reversed);
      return reversed;
    }

    private Node getParent(Node base) {
      return base.getDownEdges().get(0).getDownNode();
    }

    @Override
    public List<RebaseCommand> getRebaseCommands() {
      List<RebaseCommand> result = new ArrayList<RebaseCommand>();
      int fixupPos = 0;
      for (FakeCommitParents fakeCommit : fakeBranch) {
        Hash hash = fakeCommit.getHash();
        String newMessage = reworded.get(hash);
        RebaseCommand command;
        if (fixedUp.contains(hash)) {
          command = new RebaseCommand(RebaseCommand.RebaseCommandKind.FIXUP, fakeCommit.getCommand().getCommit());
          result.add(fixupPos, command);
          continue;
        }
        else if (newMessage != null) {
          command = new RebaseCommand(RebaseCommand.RebaseCommandKind.REWORD, fakeCommit.getCommand().getCommit(), newMessage);
        }
        else {
          command = fakeCommit.getCommand();
        }
        result.add(command);
        fixupPos = result.size() - 1;
      }
      return result;
    }

    public FakeCommitsInfo getFakeCommitsInfo() {
      return new FakeCommitsInfo(fakeBranch, branchBase, insertAfter, resultRef, subjectRef);
    }

    public void applied(RebaseCommand command) {
      ((GraphTableModel)getGraphTableModel()).addApplied(command.getCommit());
    }
  }

  private class Callback implements VcsLogActionHandler.Callback {
    @Override
    public void disableModifications() {

    }

    @Override
    public void enableModifications() {

    }

    @Override
    public void interactiveCommandApplied(RebaseCommand command) {
      rebaseDelegate.applied(command);
    }
  }

}
