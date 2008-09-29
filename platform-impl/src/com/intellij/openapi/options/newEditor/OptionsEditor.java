package com.intellij.openapi.options.newEditor;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
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

public class OptionsEditor extends JPanel implements DataProvider, Place.Navigator, Disposable {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.newEditor.OptionsEditor");

  @NonNls private static final String SPLITTER_PROPORTION = "options.splitter.proportions";

  Project myProject;

  History myHistory = new History(this);

  OptionsTree myTree;
  JTextField mySearch;
  Splitter mySplitter;


  public OptionsEditor(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    myProject = project;

    myTree = new OptionsTree(myProject, groups, new Filter());
    Disposer.register(this, myTree);
    mySearch = new JTextField();
    
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    toolbarActions.add(new BackAction(myTree));
    toolbarActions.add(new ForwardAction(myTree));
    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, true);
    tb.setTargetComponent(this);
    final JComponent toolbar = tb.getComponent();


    final JPanel left = new JPanel(new BorderLayout());
    left.add(toolbar, BorderLayout.NORTH);
    left.add(myTree, BorderLayout.CENTER);
    left.add(mySearch, BorderLayout.SOUTH);

    setLayout(new BorderLayout());

    mySplitter = new Splitter(false);
    mySplitter.setFirstComponent(left);
    mySplitter.setSecondComponent(new JPanel());
    mySplitter.setHonorComponentsMinimumSize(false);

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
  }


  public Object getData(@NonNls final String dataId) {
    return History.KEY.getName().equals(dataId) ? myHistory : null;
  }

  private class Filter implements ElementFilter {
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
}