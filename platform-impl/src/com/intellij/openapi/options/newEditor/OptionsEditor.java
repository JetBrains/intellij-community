package com.intellij.openapi.options.newEditor;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.navigation.BackAction;
import com.intellij.ui.navigation.ForwardAction;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.speedSearch.SpeedSearch;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

public class OptionsEditor extends JPanel implements DataProvider, Place.Navigator, Disposable, OptionsEditorContext, OptionsEditorContext.Listener {

  static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.newEditor.OptionsEditor");

  @NonNls static final String SPLITTER_PROPORTION = "options.splitter.proportions";

  Project myProject;

  History myHistory = new History(this);

  OptionsTree myTree;
  JTextField mySearch;
  Splitter mySplitter;
  Filter<Configurable> myFilter;
  JComponent myToolbar;

  DetailsComponent myDetails = new DetailsComponent().setEmptyContentText("Select configuration element in the tree to edit its settings");

  CopyOnWriteArraySet<Listener> myListeners = new CopyOnWriteArraySet<Listener>();

  Map<Configurable, JComponent> myConfigurable2Componenet = new HashMap<Configurable, JComponent>();
  Configurable myCurrentConfigurable;

  public OptionsEditor(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    myProject = project;

    myFilter = new Filter<Configurable>();

    myTree = new OptionsTree(myProject, groups, this);
    myListeners.add(myTree);
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

    myListeners.add(this);

    if (preselectedConfigurable != null) {
      myTree.select(preselectedConfigurable);
    } else {
      myTree.selectFirst();
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

  public void select(final Configurable configurable, @NotNull final Listener requestor) {
    for (Iterator<Listener> iterator = myListeners.iterator(); iterator.hasNext();) {
      Listener each = iterator.next();
      if (each != requestor) {
        each.onSelected(configurable);
      }
    }
  }

  @NotNull
  public ElementFilter<Configurable> getFilter() {
    return myFilter;
  }

  public void onSelected(final Configurable configurable) {
    if (configurable == null) {
      myDetails.setContent(null);
    } else {
      JComponent c = myConfigurable2Componenet.get(configurable);
      if (c == null) {
        c = configurable.createComponent();
        myConfigurable2Componenet.put(configurable, c);
      }
      myDetails.setContent(c);
      myDetails.setBannerMinHeight(myToolbar.getHeight());
      myDetails.setText(configurable.getDisplayName());
    }

    myCurrentConfigurable = configurable;
  }
}