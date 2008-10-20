package com.intellij.openapi.options.newEditor;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.ui.search.ConfigurableHit;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.ui.navigation.BackAction;
import com.intellij.ui.navigation.ForwardAction;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
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

  Project myProject;

  OptionsEditorContext myContext;

  History myHistory = new History(this);

  OptionsTree myTree;
  MySearchField mySearch;
  Splitter myMainSplitter;
  JComponent myToolbar;

  DetailsComponent myOwnDetails = new DetailsComponent().setEmptyContentText("Select configuration element in the tree to edit its settings");
  ContentWrapper myContentWrapper = new ContentWrapper();


  Map<Configurable, ConfigurableContent> myConfigurable2Content = new HashMap<Configurable, ConfigurableContent>();
  Map<Configurable, ActionCallback> myConfigurable2LoadCallback = new HashMap<Configurable, ActionCallback>();

  private MyColleague myColleague;

  MergingUpdateQueue myModificationChecker;
  private ConfigurableGroup[] myGroups;

  private SpotlightPainter mySpotlightPainter = new SpotlightPainter();
  private MergingUpdateQueue mySpotlightUpdate;
  private LoadingDecorator myLoadingDecorator;
  private Filter myFilter;

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
        mySearch.keyEventToTextField(e);
      }
    };
    getContext().addColleague(myTree);
    Disposer.register(this, myTree);
    mySearch.addDocumentListener(new DocumentListener() {
      public void insertUpdate(final DocumentEvent e) {
        myFilter.update(e);
      }

      public void removeUpdate(final DocumentEvent e) {
        myFilter.update(e);
      }

      public void changedUpdate(final DocumentEvent e) {
        myFilter.update(e);
      }
    });


    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    toolbarActions.add(new BackAction(myTree));
    toolbarActions.add(new ForwardAction(myTree));
    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, true);
    tb.setTargetComponent(this);
    final JComponent toolbar = tb.getComponent();


    final JPanel left = new JPanel(new BorderLayout());
    myToolbar = toolbar;
    left.add(myToolbar, BorderLayout.NORTH);
    left.add(myTree, BorderLayout.CENTER);
    left.add(mySearch, BorderLayout.SOUTH);

    setLayout(new BorderLayout());

    myMainSplitter = new Splitter(false);
    myMainSplitter.setFirstComponent(left);
    myMainSplitter.setHonorComponentsMinimumSize(false);

    myLoadingDecorator = new LoadingDecorator(myOwnDetails.getComponent(), project);
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

    IdeGlassPaneUtil.installPainter(myContentWrapper, mySpotlightPainter, this);
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

  private void processSelected(final Configurable configurable, final Configurable oldConfigurable) {
    if (configurable == null) {
      myOwnDetails.setContent(null);

      updateSpotlight(true);
      checkModified(oldConfigurable);
    } else {
      getUiFor(configurable).doWhenDone(new EdtRunnable() {
        public void runEdt() {
          final Configurable current = getContext().getCurrentConfigurable();
          if (current != configurable) return;


          updateDetails();

          myOwnDetails.setContent(myContentWrapper);
          myOwnDetails.setBannerMinHeight(myToolbar.getHeight());
          myOwnDetails.setText(getBannerText(configurable));


          myOwnDetails.setBannerActions(new Action[] {new ResetAction(configurable)});
          myOwnDetails.updateBannerActions();

          myLoadingDecorator.stopLoading();

          updateSpotlight(true);

          checkModified(oldConfigurable);
          checkModified(configurable);
        }
      });
    }
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
          initConfigurable(configurable, result);
        }
      });

    } else {
      result.setDone();
    }

    return result;
  }

  private void initConfigurable(final Configurable configurable, final ActionCallback result) {
    final Ref<ConfigurableContent> content = new Ref<ConfigurableContent>();

    if (configurable instanceof MasterDetails) {
      content.set(new Details((MasterDetails)configurable));
    } else {
      content.set(new Simple(configurable));
    }

    if (!myConfigurable2Content.containsKey(configurable)) {
      configurable.reset();
    }

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        myConfigurable2Content.put(configurable, content.get());
        result.setDone();
        myConfigurable2LoadCallback.remove(configurable);
      }
    });
  }


  private void updateSpotlight(boolean now) {
    if (now) {
      mySpotlightPainter.updateForCurrentConfigurable();
    } else {
      mySpotlightUpdate.queue(new Update(this) {
        public void run() {
          mySpotlightPainter.updateForCurrentConfigurable();
        }
      });
    }
  }

  private String[] getBannerText(Configurable configurable) {
    final List<Configurable> list = myTree.getPathToRoot(configurable);
    final String[] result = new String[list.size()];
    int add = 0;
    for (int i = list.size() - 1; i >=0; i--) {
      result[add++] = list.get(i).getDisplayName();
    }
    return result;
  }

  private void checkModified(final Configurable configurable) {
    fireModification(configurable);
  }

  private void fireModification(final Configurable actual) {
    if (!myConfigurable2Content.containsKey(actual)) return;

    if (actual != null && actual.isModified()) {
      getContext().fireModifiedAdded(actual, null);
    } else if (actual != null && !actual.isModified() && !getContext().getErrors().containsKey(actual)) {
      getContext().fireModifiedRemoved(actual, null);
    }
  }

  private void updateDetails() {
    final Configurable current = getContext().getCurrentConfigurable();
    final ConfigurableContent content = myConfigurable2Content.get(current);
    content.set(myContentWrapper);
  }

  @Nullable
  public String getHelpTopic() {
    final Configurable current = getContext().getCurrentConfigurable();
    return current != null ? current.getHelpTopic() : null;
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
        getContext().fireModifiedRemoved(each, myColleague);
      }
      catch (ConfigurationException e) {
        errors.put(each, e);
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


    public boolean shouldBeShowing(final SimpleNode value) {
      if (myFiltered == null) return true;

      if (value instanceof OptionsTree.EditorNode) {
        return myFiltered.contains(((OptionsTree.EditorNode)value).getConfigurable());
      }

      return true;
    }

    public void update(DocumentEvent e) {
      final String text = mySearch.getText();
      if (text == null || text.length() == 0) {
        myContext.setHoldingFilter(false);
        myFiltered = null;
      } else {
        myContext.setHoldingFilter(true);
        myHits = myIndex.getConfigurables(myGroups, e.getType(), myFiltered, text, myProject);
        myFiltered = myHits.getAll();
      }

      if (myFiltered != null && myFiltered.size() == 0) {
        mySearch.getTextEditor().setBackground(LightColors.RED);
      } else {
        mySearch.getTextEditor().setBackground(UIUtil.getTextFieldBackground());
      }


      Configurable toSelect = null;
      final Configurable current = getContext().getCurrentConfigurable();
      if (myFiltered == null || !myFiltered.contains(current)) {
        if (myHits != null) {
          if (myHits.getNameHits().size() > 0) {
            toSelect = myHits.getNameHits().iterator().next();
          } else if (myHits.getContentHits().size() > 0) {
            toSelect = myHits.getContentHits().iterator().next();
          }
        }
      }

      updateSpotlight(false);

      fireUpdate(myTree.findNodeFor(toSelect));
    }

    private boolean isEmptyParent(Configurable configurable) {
      return false;
    }
  }

  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    return new ActionCallback.Done();
  }

  public void queryPlace(@NotNull final Place place) {
  }

  public void dispose() {
    final PropertiesComponent props = PropertiesComponent.getInstance(myProject);
    props.setValue(MAIN_SPLITTER_PROPORTION, String.valueOf(myMainSplitter.getProportion()));
    props.setValue(DETAILS_SPLITTER_PROPORTION, String.valueOf(myContentWrapper.myLastSplitterProproprtion));

    Toolkit.getDefaultToolkit().removeAWTEventListener(this);


    final Set<Configurable> configurables = myConfigurable2Content.keySet();
    for (Iterator<Configurable> iterator = configurables.iterator(); iterator.hasNext();) {
      Configurable each = iterator.next();
      each.disposeUIResources();
    }
  }

  public OptionsEditorContext getContext() {
    return myContext;
  }

  private class MyColleague extends OptionsEditorColleague.Adapter {
    public void onSelected(final Configurable configurable, final Configurable oldConfigurable) {
      processSelected(configurable, oldConfigurable);
    }

    @Override
    public void onModifiedRemoved(final Configurable configurable) {
      updateIfCurrent(configurable);
    }

    @Override
    public void onModifiedAdded(final Configurable configurable) {
      updateIfCurrent(configurable);
    }

    @Override
    public void onErrorsChanged() {
      updateIfCurrent(getContext().getCurrentConfigurable());
    }

    private void updateIfCurrent(final Configurable configurable) {
      if (getContext().getCurrentConfigurable() == configurable) {
        updateDetails();
        myOwnDetails.updateBannerActions();
      }
    }
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
      if (SwingUtilities.isDescendingFrom(me.getComponent(), myContentWrapper)) {
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

    Configurable myLastConfigurable;
    String myLastText;

    GlassPanel myGP = new GlassPanel(myContentWrapper);
    boolean myVisible;

    public void executePaint(final Component component, final Graphics2D g) {
      if (myVisible && myGP.isVisible()) {
        myGP.paintSpotlight(g, myContentWrapper);
      }
    }

    public void updateForCurrentConfigurable() {
      final Configurable current = getContext().getCurrentConfigurable();
      final String text = mySearch.getText();

      try {
        if (myLastConfigurable == current && myLastText != null && myLastText.equals(text)) return;

        if (!(current instanceof SearchableConfigurable)) {
          myVisible = false;
          myGP.clear();
          return;
        }

        myGP.clear();

        final SearchableConfigurable searchable = (SearchableConfigurable)current;
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
        myLastConfigurable = current;
        myContentWrapper.repaint();
      }
    }

    @Override
    public boolean needsRepaint() {
      return true;
    }
  }


  abstract class ConfigurableContent {
    abstract void set(ContentWrapper wrapper);
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
  }

  public void clearFilter() {
    mySearch.setText("");
  }

}