package com.intellij.openapi.options.newEditor;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.LightColors;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.navigation.BackAction;
import com.intellij.ui.navigation.ForwardAction;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;

public class OptionsEditor extends JPanel implements DataProvider, Place.Navigator, Disposable {

  static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.newEditor.OptionsEditor");

  @NonNls static final String SPLITTER_PROPORTION = "options.splitter.proportions";

  Project myProject;

  OptionsEditorContext myContext;

  History myHistory = new History(this);

  OptionsTree myTree;
  JTextField mySearch;
  Splitter mySplitter;
  JComponent myToolbar;

  DetailsComponent myDetails = new DetailsComponent().setEmptyContentText("Select configuration element in the tree to edit its settings");
  ContentWrapper myContentWrapper = new ContentWrapper();


  Map<Configurable, JComponent> myConfigurable2Componenet = new HashMap<Configurable, JComponent>();
  private MyColleague myColleague;

  public OptionsEditor(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    myProject = project;

    myContext = new OptionsEditorContext(new Filter<Configurable>());

    myTree = new OptionsTree(myProject, groups, getContext());
    getContext().addColleague(myTree);
    Disposer.register(this, myTree);
    mySearch = new JTextField();

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
  }

  private void processSelected(final Configurable configurable, Configurable oldConfigurable) {
    if (oldConfigurable != null && oldConfigurable.isModified()) {
      getContext().fireModifiedAdded(oldConfigurable, myColleague);
    } else if (oldConfigurable != null && !oldConfigurable.isModified() && !getContext().getErrors().containsKey(oldConfigurable)) {
      getContext().fireModifiedRemoved(oldConfigurable, myColleague);
    }

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
      myDetails.setText(configurable.getDisplayName());

      if (reset) {
        reset(configurable);
      }

      myDetails.setBannerActions(new Action[] {new ResetAction(configurable)});
      myDetails.updateBannerActions();
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

  private class ContentWrapper extends NonOpaquePanel {

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


  private class Filter<Configurable> implements ElementFilter {
    public boolean shouldBeShowing(final Object value) {
      return true;
    }

    public SpeedSearch getSpeedSearch() {
      return new SpeedSearch() {
        protected void update() {
        }
      };
    }
  }

  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    return new ActionCallback.Done();
  }

  public void queryPlace(@NotNull final Place place) {
  }

  public void dispose() {
    PropertiesComponent.getInstance(myProject).setValue(SPLITTER_PROPORTION, String.valueOf(mySplitter.getProportion()));
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
}