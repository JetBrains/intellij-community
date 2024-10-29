// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.options;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.compiler.Validator;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfigurable;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class ValidationConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  private JPanel myPanel;
  private JCheckBox myValidateBox;
  private ElementsChooser<Validator> myValidators;
  private JPanel myExcludedEntriesPanel;
  private JPanel myValidatorsPanel;
  private final Project myProject;
  private final ValidationConfiguration myConfiguration;
  private final ExcludedEntriesConfigurable myExcludedConfigurable;

  public ValidationConfigurable(final Project project) {
    myProject = project;
    myConfiguration = ValidationConfiguration.getInstance(myProject);
    myExcludedConfigurable = createExcludedConfigurable(project);

    myValidatorsPanel.setBorder(IdeBorderFactory.createTitledBorder(JavaCompilerBundle.message("settings.validators"), false, JBUI.insetsTop(8)).setShowLine(false));
    myValidators.getEmptyText().setText(JavaCompilerBundle.message("no.validators"));

    myExcludedEntriesPanel.setBorder(IdeBorderFactory.createTitledBorder(JavaCompilerBundle.message("settings.exclude.from.validation"), false, JBUI.insetsTop(8)).setShowLine(false));
  }

  private static ExcludedEntriesConfigurable createExcludedConfigurable(@NotNull Project project) {
    var index = project.isDefault() ? null : ProjectRootManager.getInstance(project).getFileIndex();
    var descriptor = new FileChooserDescriptor(true, true, false, false, false, true)
      .withFileFilter(file -> index == null || !index.isExcluded(file));

    List<VirtualFile> allContentRoots = new ArrayList<>();
    for (final Module module: ModuleManager.getInstance(project).getModules()) {
      final VirtualFile[] moduleContentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      Collections.addAll(allContentRoots, moduleContentRoots);
    }
    descriptor.setRoots(allContentRoots);
    return new ExcludedEntriesConfigurable(project, descriptor, ValidationConfiguration.getExcludedEntriesConfiguration(project));
  }

  @Override
  @NotNull
  public String getId() {
    return "project.validation";
  }

  @Override
  public String getDisplayName() {
    return JavaCompilerBundle.message("validation.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.projectsettings.compiler.validation";
  }

  @Override
  public JComponent createComponent() {
    final GridConstraints constraints = new GridConstraints();
    constraints.setFill(GridConstraints.FILL_BOTH);
    myExcludedEntriesPanel.add(myExcludedConfigurable.createComponent(), constraints);
    return myPanel;
  }

  @Override
  public boolean isModified() {
    List<Validator> selectedElements = myValidators.getMarkedElements();
    List<Validator> markedValidators = getMarkedValidators(ContainerUtil.concat(selectedElements, myValidators.getElements(false)));
    if (markedValidators.size() != selectedElements.size()) {
      return true;
    }
    Set<Validator> set = CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
      @Override
      public int hashCode(Validator object) {
        return object==null?0:object.getId().hashCode();
      }

      @Override
      public boolean equals(Validator o1, Validator o2) {
        return (o1==null?"":o1.getId()).equals(o2==null?"":o2.getId());
      }
    });
    set.addAll(selectedElements);
    return myConfiguration.isValidateOnBuild() != myValidateBox.isSelected() ||
        set.retainAll(markedValidators) ||
        myExcludedConfigurable.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfiguration.setValidateOnBuild(myValidateBox.isSelected());
    for (int i = 0; i < myValidators.getElementCount(); i++) {
      final Validator validator = myValidators.getElementAt(i);
      myConfiguration.setSelected(validator,  myValidators.isElementMarked(validator));
    }
    myExcludedConfigurable.apply();
  }

  @Override
  public void reset() {
    myValidateBox.setSelected(myConfiguration.isValidateOnBuild());
    final List<Validator> allValidators = getValidators();
    allValidators.sort(Comparator.comparing(Validator::getDescription));
    myValidators.setElements(allValidators, false);
    myValidators.markElements(getMarkedValidators(allValidators));
    myExcludedConfigurable.reset();
  }

  private List<Validator> getMarkedValidators(@NotNull List<Validator> validators) {
    return ContainerUtil.mapNotNull(validators, (NullableFunction<Validator, Validator>)validator -> myConfiguration.isSelected(validator) ? validator : null);
  }

  private List<Validator> getValidators() {
    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    return new SmartList<>(compilerManager.getCompilers(Validator.class));
  }

  @Override
  public void disposeUIResources() {
    myExcludedConfigurable.disposeUIResources();
  }

  private void createUIComponents() {
    myValidators = new ElementsChooser<>(true) {
      @Override
      protected String getItemText(@NotNull final Validator validator) {
        return validator.getDescription();
      }
    };
  }
}
