package com.intellij.openapi.options.newEditor;

import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
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
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class OptionsEditor extends JPanel implements DataProvider, Place.Navigator, Disposable, AWTEventListener {

  static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.newEditor.OptionsEditor");

  @NonNls static final String SPLITTER_PROPORTION = "options.splitter.proportions";

  Project myProject;

  OptionsEditorContext myContext;

  History myHistory = new History(this);

  OptionsTree myTree;
  MySearchField mySearch;
  Splitter mySplitter;
  JComponent myToolbar;
                             
  DetailsComponent myDetails = new DetailsComponent().setEmptyContentText("Select configuration element in the tree to edit its settings");
  ContentWrapper myContentWrapper = new ContentWrapper();


  Map<Configurable, JComponent> myConfigurable2Componenet = new HashMap<Configurable, JComponent>();
  private MyColleague myColleague;

  MergingUpdateQueue myModificationChecker;
  private ConfigurableGroup[] myGroups;

  public OptionsEditor(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    myProject = project;
    myGroups = groups;

    final Filter filter = new Filter();
    myContext = new OptionsEditorContext(filter);

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
        filter.update(e);
      }

      public void removeUpdate(final DocumentEvent e) {
        filter.update(e);
      }

      public void changedUpdate(final DocumentEvent e) {
        filter.update(e);
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

    mySplitter = new Splitter(false);
    mySplitter.setFirstComponent(left);
    mySplitter.setHonorComponentsMinimumSize(false);

    mySplitter.setSecondComponent(myDetails.getComponent());

    float proportion = .3f;
    try {
      final String p = PropertiesComponent.getInstance(myProject).getValue(SPLITTER_PROPORTION);
      if (p != null) {
        proportion = Float.valueOf(p);
      }
    }
    catch (NumberFormatException e) {
      LOG.debug(e);
    }


    mySplitter.setProportion(proportion);

    add(mySplitter, BorderLayout.CENTER);

    myColleague = new MyColleague();
    getContext().addColleague(myColleague);

    if (preselectedConfigurable != null) {
      myTree.select(preselectedConfigurable);
    } else {
      myTree.selectFirst();
    }

    Toolkit.getDefaultToolkit().addAWTEventListener(this, MouseEvent.MOUSE_EVENT_MASK | KeyEvent.KEY_EVENT_MASK);

    myModificationChecker = new MergingUpdateQueue("OptionsModificationChecker", 1000, false, this, this, this);
  }

  private void processSelected(final Configurable configurable, Configurable oldConfigurable) {
    checkModified(oldConfigurable);

    if (configurable == null) {
      myDetails.setContent(null);
    } else {
      JComponent c = myConfigurable2Componenet.get(configurable);
      boolean reset = false;
      if (c == null) {
        c = configurable.createComponent();
        reset = true;
        myConfigurable2Componenet.put(configurable, c);
      }

      updateErrorBanner();

      myDetails.setContent(myContentWrapper);
      myDetails.setBannerMinHeight(myToolbar.getHeight());
      myDetails.setText(getBannerText(configurable));

      if (reset) {
        reset(configurable);
      }

      myDetails.setBannerActions(new Action[] {new ResetAction(configurable)});
      myDetails.updateBannerActions();
    }
  }

  private String getBannerText(Configurable configurable) {
    final List<Configurable> list = myTree.getPathToRoot(configurable);
    StringBuffer result = new StringBuffer();
    for (int i = list.size() - 1; i >= 0; i--) {
      Configurable each = list.get(i);
      result.append(each.getDisplayName());
      if (i > 0) {
        result.append(" : ");
      }
    }

    return result.toString();
  }

  private void checkModified(final Configurable configurable) {
    if (configurable != null && configurable.isModified()) {
      getContext().fireModifiedAdded(configurable, null);
    } else if (configurable != null && !configurable.isModified() && !getContext().getErrors().containsKey(configurable)) {
      getContext().fireModifiedRemoved(configurable, null);
    }
  }

  private void updateErrorBanner() {
    final Configurable current = getContext().getCurrentConfigurable();
    final JComponent c = myConfigurable2Componenet.get(current);
    myContentWrapper.setContent(c, getContext().getErrors().get(current));
  }


  class ResetAction extends AbstractAction {
    Configurable myConfigurable;

    ResetAction(final Configurable configurable) {
      myConfigurable = configurable;
      putValue(NAME, "Reset");
      putValue(SHORT_DESCRIPTION, "Rollback changes for this configuration element");
    }

    public void actionPerformed(final ActionEvent e) {
      reset(myConfigurable);
    }

    @Override
    public boolean isEnabled() {
      return myConfigurable.isModified() || getContext().getErrors().containsKey(myConfigurable);
    }
  }

  private class ContentWrapper extends NonOpaquePanel implements NullableComponent {

    private JLabel myErrorLabel;

    private JComponent myContent;
    private ConfigurationException myException;

    private ContentWrapper() {
      setLayout(new BorderLayout());
      myErrorLabel = new JLabel();
      myErrorLabel.setOpaque(true);
      myErrorLabel.setBackground(LightColors.RED);
    }

    void setContent(JComponent c, ConfigurationException e) {
      if (myContent == c && myException == e) return;

      removeAll();
      add(c, BorderLayout.CENTER);

      if (e != null) {
        myErrorLabel.setText(UIUtil.toHtml(e.getMessage()));
        add(myErrorLabel, BorderLayout.NORTH);
      }

      myContent = c;
      myException = e;
    }

    @Override
    public boolean isNull() {
      return super.isNull() || NullableComponent.Check.isNull(myContent);
    }
  }

  public void reset(Configurable configurable) {
    configurable.reset();
    getContext().fireReset(configurable);
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


  private class Filter implements ElementFilter.Active<SimpleNode> {

    Set<Listener> myListeners = new CopyOnWriteArraySet<Listener>();

    SearchableOptionsRegistrar myIndex = SearchableOptionsRegistrar.getInstance();
    Set<Configurable> myOptionContainers = null;


    public boolean shouldBeShowing(final SimpleNode value) {
      if (myOptionContainers == null) return true;
      
      if (value instanceof OptionsTree.EditorNode) {
        final OptionsTree.EditorNode node = (OptionsTree.EditorNode)value;
        if (myOptionContainers.contains(node.getConfigurable())) return true;

        SimpleNode eachParent = node.getParent();
        while (eachParent != null) {
          if (eachParent instanceof OptionsTree.EditorNode) {
            final OptionsTree.EditorNode eachParentEditor = (OptionsTree.EditorNode)eachParent;
            final Configurable eachParentConfigurable = eachParentEditor.getConfigurable();
            if (myOptionContainers.contains(eachParentConfigurable) && eachParentConfigurable instanceof SearchableConfigurable.Parent) {
              final SearchableConfigurable.Parent eachParentSearchable = (SearchableConfigurable.Parent)eachParentConfigurable;
              if (eachParentSearchable.isResponsibleForChildren()) return true;
            }
          }

          eachParent = eachParent.getParent();
        }

        return false;
      } else {
        return true;
      }
    }

    public void update(DocumentEvent e) {
      final String text = mySearch.getText();
      if (text == null || text.length() == 0) {
        myOptionContainers = null;
      } else {
        myOptionContainers = myIndex.getConfigurables(myGroups, e.getType(), myOptionContainers, text, myProject);
      }

      if (myOptionContainers != null && myOptionContainers.size() == 0) {
        mySearch.getTextEditor().setBackground(LightColors.RED);
      } else {
        mySearch.getTextEditor().setBackground(UIUtil.getTextFieldBackground());
      }

      fireUpdate();
    }

    public void fireUpdate() {
      for (Iterator<Listener> iterator = myListeners.iterator(); iterator.hasNext();) {
        iterator.next().update();
      }
    }

    public void addListener(final Listener listener, final Disposable parent) {
      myListeners.add(listener);
      Disposer.register(parent, new Disposable() {
        public void dispose() {
          myListeners.remove(listener);
        }
      });
    }
  }

  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    return new ActionCallback.Done();
  }

  public void queryPlace(@NotNull final Place place) {
  }

  public void dispose() {
    PropertiesComponent.getInstance(myProject).setValue(SPLITTER_PROPORTION, String.valueOf(mySplitter.getProportion()));
    Toolkit.getDefaultToolkit().removeAWTEventListener(this);



    final Set<Configurable> configurables = myConfigurable2Componenet.keySet();
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
        updateErrorBanner();
        myDetails.updateBannerActions();
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
          final Object action = getTextEditor().getInputMap().get(stroke);
          if (action == null) {
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

}