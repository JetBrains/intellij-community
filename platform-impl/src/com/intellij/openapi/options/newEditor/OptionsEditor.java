package com.intellij.openapi.options.newEditor;

import com.intellij.ide.ui.search.ConfigurableHit;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.*;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EdtRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.LightColors;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class OptionsEditor extends JPanel implements DataProvider, Place.Navigator, Disposable, AWTEventListener {

  static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.newEditor.OptionsEditor");

  @NonNls static final String MAIN_SPLITTER_PROPORTION = "options.splitter.main.proportions";
  @NonNls static final String DETAILS_SPLITTER_PROPORTION = "options.splitter.details.proportions";
  @NonNls static final String LAST_SELECTED_CONFIGURABLE = "options.lastSelected";

  @NonNls static final String SEARCH_VISIBLE = "options.searchVisible";

  Project myProject;

  OptionsEditorContext myContext;

  History myHistory = new History(this);

  OptionsTree myTree;
  MySearchField mySearch;
  Splitter myMainSplitter;
  //[back/forward] JComponent myToolbarComponent;

  DetailsComponent myOwnDetails = new DetailsComponent().setEmptyContentText("Select configuration element in the tree to edit its settings");
  ContentWrapper myContentWrapper = new ContentWrapper();


  Map<Configurable, ConfigurableContent> myConfigurable2Content = new HashMap<Configurable, ConfigurableContent>();
  Map<Configurable, ActionCallback> myConfigurable2LoadCallback = new HashMap<Configurable, ActionCallback>();

  private MyColleague myColleague;

  MergingUpdateQueue myModificationChecker;
  private ConfigurableGroup[] myGroups;

  SpotlightPainter mySpotlightPainter = new SpotlightPainter();
  MergingUpdateQueue mySpotlightUpdate;
  LoadingDecorator myLoadingDecorator;
  Filter myFilter;

  Wrapper mySearchWrapper = new Wrapper();
  JPanel myLeftSide;

  boolean myFilterFocumentWasChanged;
  //[back/forward] private ActionToolbar myToolbar;
  private Window myWindow;

  public OptionsEditor(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    myProject = project;
    myGroups = groups;

    myFilter = new Filter();
    myContext = new OptionsEditorContext(myFilter);

    mySearch = new MySearchField() {
      @Override
      protected void onTextKeyEvent(final KeyEvent e) {
        myTree.processTextEvent(e);
      }
    };
    myTree = new OptionsTree(myProject, groups, getContext()) {
      @Override
      protected void onTreeKeyEvent(final KeyEvent e) {
        myFilterFocumentWasChanged = false;
        try {
          mySearch.keyEventToTextField(e);
        }
        finally {
          if (myFilterFocumentWasChanged && !isFilterFieldVisible()) {
            setFilterFieldVisible(true, false, false);
          }
        }
      }
    };

    getContext().addColleague(myTree);
    Disposer.register(this, myTree);
    mySearch.addDocumentListener(new DocumentListener() {
      public void insertUpdate(final DocumentEvent e) {
        myFilter.update(e.getType(), true, false);
      }

      public void removeUpdate(final DocumentEvent e) {
        myFilter.update(e.getType(), true, false);
      }

      public void changedUpdate(final DocumentEvent e) {
        myFilter.update(e.getType(), true, false);
      }
    });


    /* [back/forward]
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    toolbarActions.add(new BackAction(myTree));
    toolbarActions.add(new ForwardAction(myTree));
    toolbarActions.addSeparator();
    toolbarActions.add(new ShowSearchFieldAction(this));
    myToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, true);
    myToolbar.setTargetComponent(this);
    myToolbarComponent = myToolbar.getComponent();

    myHistory.addListener(new HistoryListener.Adapter() {
      @Override
      public void navigationFinished(final Place from, final Place to) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            if (myToolbarComponent.isShowing()) {
              myToolbar.updateActionsImmediately();
            }
          }
        });
      }
    }, this);
    */


    myLeftSide = new JPanel(new BorderLayout());

    /* [back/forward]

    final NonOpaquePanel toolbarPanel = new NonOpaquePanel(new BorderLayout());
    toolbarPanel.add(myToolbarComponent, BorderLayout.WEST);
    toolbarPanel.add(mySearchWrapper, BorderLayout.CENTER);
    */

    myLeftSide.add(mySearchWrapper, BorderLayout.NORTH);

    myLeftSide.add(myTree, BorderLayout.CENTER);

    setLayout(new BorderLayout());

    myMainSplitter = new Splitter(false);
    myMainSplitter.setFirstComponent(myLeftSide);
    myMainSplitter.setHonorComponentsMinimumSize(false);

    myLoadingDecorator = new LoadingDecorator(myOwnDetails.getComponent(), project, 150);
    myMainSplitter.setSecondComponent(myLoadingDecorator.getComponent());


    myMainSplitter.setProportion(readPropertion(.3f, MAIN_SPLITTER_PROPORTION));
    myContentWrapper.mySplitter.setProportion(readPropertion(.2f, DETAILS_SPLITTER_PROPORTION));

    add(myMainSplitter, BorderLayout.CENTER);

    myColleague = new MyColleague();
    getContext().addColleague(myColleague);

    if (preselectedConfigurable != null) {
      myTree.select(preselectedConfigurable);
    } else {
      myTree.selectFirst();
    }

    Toolkit.getDefaultToolkit().addAWTEventListener(this, MouseEvent.MOUSE_EVENT_MASK | KeyEvent.KEY_EVENT_MASK);

    myModificationChecker = new MergingUpdateQueue("OptionsModificationChecker", 1000, false, this, this, this);
    mySpotlightUpdate = new MergingUpdateQueue("OptionsSplotlight", 500, false, this, this, this);

    IdeGlassPaneUtil.installPainter(myOwnDetails.getContentGutter(), mySpotlightPainter, this);

    /*
    String visible = PropertiesComponent.getInstance(myProject).getValue(SEARCH_VISIBLE);
    if (visible == null) {
      visible = "true";
    }
    */

    setFilterFieldVisible(true, false, false);

    new UiNotifyConnector.Once(this, new Activatable() {
      public void showNotify() {
        myWindow = SwingUtilities.getWindowAncestor(OptionsEditor.this);
      }

      public void hideNotify() {
      }
    });
  }

  private float readPropertion(final float defaultValue, final String propertyName) {
    float proportion = defaultValue;
    try {
      final String p = PropertiesComponent.getInstance(myProject).getValue(propertyName);
      if (p != null) {
        proportion = Float.valueOf(p);
      }
    }
    catch (NumberFormatException e) {
      LOG.debug(e);
    }
    return proportion;
  }

  private ActionCallback processSelected(final Configurable configurable, final Configurable oldConfigurable) {
    if (isShowing(configurable)) return ActionCallback.DONE;

    final ActionCallback result = new ActionCallback();
    
    if (configurable == null) {
      myOwnDetails.setContent(null);

      updateSpotlight(true);
      checkModified(oldConfigurable);

      result.setDone();

    } else {
      getUiFor(configurable).doWhenDone(new EdtRunnable() {
        public void runEdt() {
          final Configurable current = getContext().getCurrentConfigurable();
          if (current != configurable) {
            result.setRejected();
            return;
          }

          myHistory.pushQueryPlace();

          updateDetails();

          myOwnDetails.setContent(myContentWrapper);
          myOwnDetails.setBannerMinHeight(mySearchWrapper.getHeight());
          myOwnDetails.setText(getBannerText(configurable));


          myOwnDetails.setBannerActions(new Action[] {new ResetAction(configurable)});
          myOwnDetails.updateBannerActions();

          myLoadingDecorator.stopLoading();

          updateSpotlight(true);

          checkModified(oldConfigurable);
          checkModified(configurable);

          result.setDone();
        }
      });
    }

    return result;
  }

  private ActionCallback getUiFor(final Configurable configurable) {
    final ActionCallback result = new ActionCallback();

    if (!myConfigurable2Content.containsKey(configurable)) {

      final ActionCallback readyCallback = myConfigurable2LoadCallback.get(configurable);
      if (readyCallback != null) {
        return readyCallback;
      }

      myConfigurable2LoadCallback.put(configurable, result);
      myLoadingDecorator.startLoading(false);
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          initConfigurable(configurable).notifyWhenDone(result);
        }
      });

    } else {
      result.setDone();
    }

    return result;
  }

  private ActionCallback initConfigurable(final Configurable configurable) {
    final ActionCallback result = new ActionCallback();

    final Ref<ConfigurableContent> content = new Ref<ConfigurableContent>();

    if (configurable instanceof MasterDetails) {
      content.set(new Details((MasterDetails)configurable));
    } else {
      content.set(new Simple(configurable));
    }

    if (!myConfigurable2Content.containsKey(configurable)) {
      if (configurable instanceof Place.Navigator) {
        ((Place.Navigator)configurable).setHistory(myHistory);
      }

      configurable.reset();
    }

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        myConfigurable2Content.put(configurable, content.get());
        result.setDone();
      }
    });

    return result;
  }


  private void updateSpotlight(boolean now) {
    if (now) {
      final boolean success = mySpotlightPainter.updateForCurrentConfigurable();
      if (!success) {
        updateSpotlight(false);
      }
    } else {
      mySpotlightUpdate.queue(new Update(this) {
        public void run() {
          final boolean success = mySpotlightPainter.updateForCurrentConfigurable();
          if (!success) {
            updateSpotlight(false);
          }
        }
      });
    }
  }

  private String[] getBannerText(Configurable configurable) {
    final List<Configurable> list = myTree.getPathToRoot(configurable);
    final String[] result = new String[list.size()];
    int add = 0;
    for (int i = list.size() - 1; i >=0; i--) {
      result[add++] = list.get(i).getDisplayName().replace('\n', ' ');
    }
    return result;
  }

  private void checkModified(final Configurable configurable) {
    fireModification(configurable);
  }

  private void fireModification(final Configurable actual) {

    Collection<Configurable> toCheck = colectAllParentsAndSiblings(actual);

    for (Configurable configurable : toCheck) {
      fireModificationForItem(configurable);
    }

  }

  private Collection<Configurable> colectAllParentsAndSiblings(final Configurable actual) {
    ArrayList<Configurable> result = new ArrayList<Configurable>();
    Configurable nearestParent = getContext().getParentConfigurable(actual);

    if (nearestParent != null) {
      Configurable parent = nearestParent;
      while (parent != null) {
        result.add(parent);
        parent = getContext().getParentConfigurable(parent);
      }

      result.addAll(getContext().getChildren(nearestParent));
    } else {
      result.add(actual);
    }


    return result;
  }

  private void fireModificationForItem(final Configurable configurable) {
    if (configurable != null) {
      if (!myConfigurable2Content.containsKey(configurable) && isParentWithContent(configurable)) {

        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            initConfigurable(configurable).doWhenDone(new Runnable() {
              public void run() {
                fireModifiationInt(configurable);
              }
            });
          }
        });
      }
      else if (myConfigurable2Content.containsKey(configurable)) {
        fireModifiationInt(configurable);
      }
    }
  }

  private static boolean isParentWithContent(final Configurable configurable) {
    return configurable instanceof SearchableConfigurable.Parent &&
        ((SearchableConfigurable.Parent)configurable).hasOwnContent();
  }

  private void fireModifiationInt(final Configurable configurable) {
    if (configurable.isModified()) {
      getContext().fireModifiedAdded(configurable, null);
    } else if (!configurable.isModified() && !getContext().getErrors().containsKey(configurable)) {
      getContext().fireModifiedRemoved(configurable, null);
    }
  }

  private void updateDetails() {
    final Configurable current = getContext().getCurrentConfigurable();
    final ConfigurableContent content = myConfigurable2Content.get(current);
    content.set(myContentWrapper);
  }

  private boolean isShowing(Configurable configurable) {
    final ConfigurableContent content = myConfigurable2Content.get(configurable);
    return content != null && content.isShowing();
  }

  @Nullable
  public String getHelpTopic() {
    Configurable current = getContext().getCurrentConfigurable();
    while (current != null) {
      String topic = current.getHelpTopic();
      if (topic != null) return topic;
      current = getContext().getParentConfigurable(current);
    }
    return null;
  }

  public boolean isFilterFieldVisible() {
    return mySearch.getParent() == mySearchWrapper;
  }

  public void setFilterFieldVisible(final boolean visible, boolean requestFocus, boolean checkFocus) {
    if (isFilterFieldVisible() && checkFocus && requestFocus && !isSearchFieldFocused()) {
      UIUtil.requestFocus(mySearch);
      return;
    }

    mySearchWrapper.setContent(visible ? mySearch : null);

    myLeftSide.revalidate();
    myLeftSide.repaint();

    if (visible && requestFocus) {
      UIUtil.requestFocus(mySearch);
    }
  }

  public boolean isSearchFieldFocused() {
    return mySearch.getTextEditor().isFocusOwner();
  }

  class ResetAction extends AbstractAction {
    Configurable myConfigurable;

    ResetAction(final Configurable configurable) {
      myConfigurable = configurable;
      putValue(NAME, "Reset");
      putValue(SHORT_DESCRIPTION, "Rollback changes for this configuration element");
    }

    public void actionPerformed(final ActionEvent e) {
      reset(myConfigurable, true);
      checkModified(myConfigurable);
    }

    @Override
    public boolean isEnabled() {
      return myContext.isModified(myConfigurable) || getContext().getErrors().containsKey(myConfigurable); 
    }
  }

  private class ContentWrapper extends NonOpaquePanel implements NullableComponent {

    private JLabel myErrorLabel;

    private JComponent mySimpleContent;
    private ConfigurationException myException;


    private JComponent myMaster;
    private JComponent myToolbar;
    private DetailsComponent myDetails;

    private Splitter mySplitter = new Splitter(false);
    private JPanel myLeft = new JPanel(new BorderLayout());
    public float myLastSplitterProproprtion;

    private ContentWrapper() {
      setLayout(new BorderLayout());
      myErrorLabel = new JLabel();
      myErrorLabel.setOpaque(true);
      myErrorLabel.setBackground(LightColors.RED);

      myLeft = new JPanel(new BorderLayout());

      mySplitter.addPropertyChangeListener(Splitter.PROP_PROPORTION, new PropertyChangeListener() {
        public void propertyChange(final PropertyChangeEvent evt) {
          myLastSplitterProproprtion = ((Float)evt.getNewValue()).floatValue();
        }
      });
    }

    void setContent(JComponent c, ConfigurationException e) {
      if (c != null && mySimpleContent == c && myException == e) return;

      removeAll();

      if (c != null) {
        add(c, BorderLayout.CENTER);
      }

      if (e != null) {
        myErrorLabel.setText(UIUtil.toHtml(e.getMessage()));
        add(myErrorLabel, BorderLayout.NORTH);
      }

      mySimpleContent = c;
      myException = e;

      myMaster = null;
      myToolbar = null;
      myDetails = null;
      mySplitter.setFirstComponent(null);
      mySplitter.setSecondComponent(null);
    }

    void setContent(JComponent master, JComponent toolbar, DetailsComponent details, ConfigurationException e) {
      if (myMaster == master && myToolbar == toolbar && myDetails == details && myException == e) return;

      myMaster = master;
      myToolbar = toolbar;
      myDetails = details;
      myException = e;


      removeAll();
      myLeft.removeAll();

      myLeft.add(myToolbar, BorderLayout.NORTH);
      myLeft.add(myMaster, BorderLayout.CENTER);

      myDetails.setBannerMinHeight(myToolbar.getPreferredSize().height);

      mySplitter.setFirstComponent(myLeft);
      mySplitter.setSecondComponent(myDetails.getComponent());
      mySplitter.setProportion(myLastSplitterProproprtion);

      add(mySplitter, BorderLayout.CENTER);

      mySimpleContent = null;
    }


    @Override
    public boolean isNull() {
      final boolean superNull = super.isNull();
      if (superNull) return superNull;

      if (myMaster == null) {
        return NullableComponent.Check.isNull(mySimpleContent);
      } else {
        return NullableComponent.Check.isNull(myMaster);
      }
    }
  }

  public void reset(Configurable configurable, boolean notify) {
    configurable.reset();
    if (notify) {
      getContext().fireReset(configurable);
    }
  }

  public void apply() {
    Map<Configurable, ConfigurationException> errors = new LinkedHashMap<Configurable, ConfigurationException>();
    final Set<Configurable> modified = getContext().getModified();
    for (Iterator<Configurable> iterator = modified.iterator(); iterator.hasNext();) {
      Configurable each = iterator.next();
      try {
        each.apply();
        if (!each.isModified()) {
          getContext().fireModifiedRemoved(each, null);
        }
      }
      catch (ConfigurationException e) {
        errors.put(each, e);
        LOG.debug(e);
      }
    }

    getContext().fireErrorsChanged(errors, null);

    if (errors.size() > 0) {
      myTree.select(errors.keySet().iterator().next());
    }
  }


  public Object getData(@NonNls final String dataId) {
    return History.KEY.getName().equals(dataId) ? myHistory : null;
  }

  public JTree getPreferredFocusedComponent() {
    return myTree.getTree();
  }


  private class Filter extends ElementFilter.Active.Impl<SimpleNode> {

    SearchableOptionsRegistrar myIndex = SearchableOptionsRegistrar.getInstance();
    Set<Configurable> myFiltered = null;
    ConfigurableHit myHits;

    boolean myUpdateEnabled = true;

    public boolean shouldBeShowing(final SimpleNode value) {
      if (myFiltered == null) return true;

      if (value instanceof OptionsTree.EditorNode) {
        final OptionsTree.EditorNode node = (OptionsTree.EditorNode)value;
        return myFiltered.contains(node.getConfigurable()) || isChildOfNameHit(node);
      }

      return true;
    }

    private boolean isChildOfNameHit(OptionsTree.EditorNode node) {
      if (myHits != null) {
        OptionsTree.Base eachParent = node;
        while (eachParent != null) {
          if (eachParent instanceof OptionsTree.EditorNode) {
            final OptionsTree.EditorNode eachEditorNode = (OptionsTree.EditorNode)eachParent;
            if (myHits.getNameFullHits().contains(eachEditorNode.myConfigurable)) return true;
          }
          eachParent = (OptionsTree.Base)eachParent.getParent();
        }

        return false;
      }

      return false;
    }

    public ActionCallback refilterFor(String text, boolean adjustSelection, final boolean now) {
      try {
        myUpdateEnabled = false;
        mySearch.setText(text);
      }
      finally {
        myUpdateEnabled = true;
      }

      return update(DocumentEvent.EventType.CHANGE, adjustSelection, now);
    }

    public ActionCallback update(DocumentEvent.EventType type, boolean adjustSeection, boolean now) {
      if (!myUpdateEnabled) return ActionCallback.REJECTED;

      final String text = mySearch.getText();
      if (getFilterText().length() == 0) {
        myContext.setHoldingFilter(false);
        myFiltered = null;
      } else {
        myContext.setHoldingFilter(true);
        myHits = myIndex.getConfigurables(myGroups, type, myFiltered, text, myProject);
        myFiltered = myHits.getAll();
      }

      if (myFiltered != null && myFiltered.size() == 0) {
        mySearch.getTextEditor().setBackground(LightColors.RED);
      } else {
        mySearch.getTextEditor().setBackground(UIUtil.getTextFieldBackground());
      }


      Configurable toSelect = null;
      final Configurable current = getContext().getCurrentConfigurable();

      boolean shouldMoveSelection = true;

      if (myHits != null && myHits.getNameFullHits().contains(current)) {
        shouldMoveSelection = false;
      }

      if (shouldMoveSelection && (myFiltered == null || myFiltered.contains(current))) {
        shouldMoveSelection = false;
      }

      if (shouldMoveSelection && myHits != null) {
        if (myHits.getNameHits().size() > 0) {
          toSelect = suggestToSelect(myHits.getNameHits(), myHits.getNameFullHits());
        } else if (myHits.getContentHits().size() > 0) {
          toSelect = suggestToSelect(myHits.getContentHits(), null);
        }
      }

      updateSpotlight(false);

      final ActionCallback callback = fireUpdate(adjustSeection ? myTree.findNodeFor(toSelect) : null, adjustSeection, now);

      myFilterFocumentWasChanged = true;

      return callback;
    }

    private boolean isEmptyParent(Configurable configurable) {
      if (configurable instanceof SearchableConfigurable.Parent) {
        return !((SearchableConfigurable.Parent)configurable).hasOwnContent();
      }
      return false;
    }

    @Nullable
    private Configurable suggestToSelect(Set<Configurable> set, Set<Configurable> fullHits) {
      Configurable candidate = null;
      for (Iterator<Configurable> iterator = set.iterator(); iterator.hasNext();) {
        Configurable each = iterator.next();
        if (fullHits != null && fullHits.contains(each)) return each;
        if (!isEmptyParent(each) && candidate == null) {
          candidate = each;
        }
      }

      return candidate;
    }

  }

  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    final Configurable config = (Configurable)place.getPath("configurable");
    final String filter = (String)place.getPath("filter");

    final ActionCallback result = new ActionCallback();

    myFilter.refilterFor(filter, false, true).doWhenDone(new Runnable() {
      public void run() {
        myTree.select(config).notifyWhenDone(result);
      }
    });

    return result;
  }

  public void queryPlace(@NotNull final Place place) {
    final Configurable current = getContext().getCurrentConfigurable();
    place.putPath("configurable", current);
    place.putPath("filter", getFilterText());

    if (current instanceof Place.Navigator) {
      ((Place.Navigator)current).queryPlace(place);
    }
  }

  public void dispose() {
    final PropertiesComponent props = PropertiesComponent.getInstance(myProject);
    props.setValue(MAIN_SPLITTER_PROPORTION, String.valueOf(myMainSplitter.getProportion()));
    props.setValue(DETAILS_SPLITTER_PROPORTION, String.valueOf(myContentWrapper.myLastSplitterProproprtion));
    props.setValue(SEARCH_VISIBLE, Boolean.valueOf(isFilterFieldVisible()).toString());

    Toolkit.getDefaultToolkit().removeAWTEventListener(this);


    final Set<Configurable> configurables = new HashSet<Configurable>();
    configurables.addAll(myConfigurable2Content.keySet());
    configurables.addAll(myConfigurable2LoadCallback.keySet());
    for (Configurable each : configurables) {
      each.disposeUIResources();
    }
  }

  public OptionsEditorContext getContext() {
    return myContext;
  }

  private class MyColleague extends OptionsEditorColleague.Adapter {
    public ActionCallback onSelected(final Configurable configurable, final Configurable oldConfigurable) {
      return processSelected(configurable, oldConfigurable);
    }

    @Override
    public ActionCallback onModifiedRemoved(final Configurable configurable) {
      return updateIfCurrent(configurable);
    }

    @Override
    public ActionCallback onModifiedAdded(final Configurable configurable) {
      return updateIfCurrent(configurable);
    }

    @Override
    public ActionCallback onErrorsChanged() {
      return updateIfCurrent(getContext().getCurrentConfigurable());
    }

    private ActionCallback updateIfCurrent(final Configurable configurable) {
      if (getContext().getCurrentConfigurable() == configurable) {
        updateDetails();
        myOwnDetails.updateBannerActions();
        return ActionCallback.DONE;
      } else {
        return ActionCallback.REJECTED;
      }
    }
  }

  public void flushModifications() {
    fireModification(getContext().getCurrentConfigurable());
  }

  public boolean canApply() {
    return getContext().getModified().size() > 0;
  }

  public boolean canRestore() {
    return getContext().getModified().size() > 0;
  }

  public void eventDispatched(final AWTEvent event) {
    if (event.getID() == MouseEvent.MOUSE_PRESSED || event.getID() == MouseEvent.MOUSE_RELEASED) {
      final MouseEvent me = (MouseEvent)event;
      if (SwingUtilities.isDescendingFrom(me.getComponent(), myContentWrapper) || isPopupOverEditor(me.getComponent())) {
        queueModificationCheck(getContext().getCurrentConfigurable());
        return;
      }
    } else if (event.getID() == KeyEvent.KEY_PRESSED || event.getID() == KeyEvent.KEY_RELEASED) {
      final KeyEvent ke = (KeyEvent)event;
      if (SwingUtilities.isDescendingFrom(ke.getComponent(), myContentWrapper)) {
        queueModificationCheck(getContext().getCurrentConfigurable());
        return;
      }
    }
  }

  private void queueModificationCheck(final Configurable configurable) {
    myModificationChecker.queue(new Update(this) {
      public void run() {
        checkModified(configurable);
      }

      @Override
      public boolean isExpired() {
        return getContext().getCurrentConfigurable() != configurable;
      }
    });
  }

  private boolean isPopupOverEditor(Component c) {
    final Window wnd = SwingUtilities.getWindowAncestor(c);
    if (wnd instanceof JWindow && myWindow != null && wnd.getParent() == myWindow) return true;
    return false;
  }

  private static class MySearchField extends SearchTextField {

    private boolean myDelegatingNow;

    private MySearchField() {
      super(false);
      addKeyListener(new KeyAdapter() {});
    }

    @Override
    protected boolean preprocessEventForTextField(final KeyEvent e) {
      if (getTextEditor().isFocusOwner() && !myDelegatingNow) {
        try {
          myDelegatingNow = true;
          final KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(e);
          boolean treeNavigation = stroke.getModifiers() == 0 && (stroke.getKeyCode() == KeyEvent.VK_UP || stroke.getKeyCode() == KeyEvent.VK_DOWN);

          final Object action = getTextEditor().getInputMap().get(stroke);
          if (action == null || treeNavigation) {
            onTextKeyEvent(e);
            return true;
          }
        }
        finally {
          myDelegatingNow = false;
        }
      }

      return false;
    }


    protected void onTextKeyEvent(final KeyEvent e) {

    }
  }

  class SpotlightPainter extends AbstractPainter {
    Map<Configurable, String> myConfigurableToLastOption = new HashMap<Configurable, String>();

    GlassPanel myGP = new GlassPanel(myOwnDetails.getContentGutter());
    boolean myVisible;

    public void executePaint(final Component component, final Graphics2D g) {
      if (myVisible && myGP.isVisible()) {
        myGP.paintSpotlight(g, myOwnDetails.getContentGutter());
      }
    }

    public boolean updateForCurrentConfigurable() {
      final Configurable current = getContext().getCurrentConfigurable();

      if (current != null && !myConfigurable2Content.containsKey(current)) return false;

      String text = getFilterText();

      try {
        if (myConfigurableToLastOption.containsKey(current) && text.equals(myConfigurableToLastOption.get(current))) return true;

        if (current == null) {
          myVisible = false;
          myGP.clear();
          return true;
        }

        SearchableConfigurable searchable;
        if (current instanceof SearchableConfigurable) {
          searchable = (SearchableConfigurable)current;
        } else {
          searchable = new SearachableWrappper(current);
        }

        myGP.clear();
        
        final Runnable runnable = SearchUtil.lightOptions(searchable, myContentWrapper, text, myGP);
        if (runnable != null) {
          myVisible = true;
          runnable.run();

          boolean pushFilteringFurther = true;
          if (myFilter.myHits != null) {
            pushFilteringFurther = !myFilter.myHits.getNameHits().contains(current);
          }
          
          final Runnable ownSearch = searchable.enableSearch(text);
          if (pushFilteringFurther && ownSearch != null) {
            ownSearch.run();
          }
          fireNeedsRepaint(myContentWrapper);
        } else {
          myVisible = false;
        }
      }
      finally {
        myConfigurableToLastOption.put(current, text);
      }

      return true;
    }


    @Override
    public boolean needsRepaint() {
      return true;
    }
  }

  private String getFilterText() {
    return mySearch.getText() != null ? mySearch.getText().trim() : "";
  }

  private class SearachableWrappper implements SearchableConfigurable {
    private Configurable myConfigurable;

    private SearachableWrappper(final Configurable configurable) {
      myConfigurable = configurable;
    }

    public String getId() {
      return myConfigurable.getClass().getName();
    }

    public Runnable enableSearch(final String option) {
      return null;
    }

    @Nls
    public String getDisplayName() {
      return myConfigurable.getDisplayName();
    }

    public Icon getIcon() {
      return myConfigurable.getIcon();
    }

    public String getHelpTopic() {
      return myConfigurable.getHelpTopic();
    }

    public JComponent createComponent() {
      return myConfigurable.createComponent();
    }

    public boolean isModified() {
      return myConfigurable.isModified();
    }

    public void apply() throws ConfigurationException {
      myConfigurable.apply();
    }

    public void reset() {
      myConfigurable.reset();
    }

    public void disposeUIResources() {
      myConfigurable.disposeUIResources();
    }
  }


  abstract class ConfigurableContent {
    abstract void set(ContentWrapper wrapper);

    abstract boolean isShowing();
  }

  class Simple extends ConfigurableContent {

    JComponent myComponent;
    Configurable myConfigurable;

    Simple(final Configurable configurable) {
      myConfigurable = configurable;
      myComponent = configurable.createComponent();
    }

    void set(final ContentWrapper wrapper) {
      myOwnDetails.setDetailsModeEnabled(true);
      wrapper.setContent(myComponent, getContext().getErrors().get(myConfigurable));
    }

    boolean isShowing() {
      return myComponent != null && myComponent.isShowing();
    }
  }

  class Details extends ConfigurableContent {

    MasterDetails myConfigurable;
    DetailsComponent myDetails;
    JComponent myMaster;
    JComponent myToolbar;

    Details(final MasterDetails configurable) {
      myConfigurable = configurable;
      myConfigurable.initUi();
      myDetails = myConfigurable.getDetails();
      myMaster = myConfigurable.getMaster();
      myToolbar = myConfigurable.getToolbar();
    }

    void set(final ContentWrapper wrapper) {
      myOwnDetails.setDetailsModeEnabled(false);
      myDetails.setPrefix(getBannerText((Configurable)myConfigurable));
      wrapper.setContent(myMaster, myToolbar, myDetails, getContext().getErrors().get(myConfigurable));
    }

    boolean isShowing() {
      return myDetails.getComponent().isShowing();
    }
  }

  public void clearFilter() {
    mySearch.setText("");
  }

  public void setHistory(final History history) {
  }
}
