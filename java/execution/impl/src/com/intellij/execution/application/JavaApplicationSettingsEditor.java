// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.ui.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Predicates;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletion.StringsCompletionProvider;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.execution.ui.CommandLinePanel.setMinimumWidth;

public final class JavaApplicationSettingsEditor extends JavaSettingsEditorBase<ApplicationConfiguration> {
  private SettingsEditorFragment<ApplicationConfiguration, MainClassPanel> myMainClassFragment;
  private final boolean myInitialIsImplicitClass;

  public JavaApplicationSettingsEditor(ApplicationConfiguration configuration) {
    super(configuration);
    myInitialIsImplicitClass = configuration.isImplicitClassConfiguration();
  }

  @Override
  public boolean isInplaceValidationSupported() {
    return true;
  }

  @Override
  protected void customizeFragments(List<SettingsEditorFragment<ApplicationConfiguration, ?>> fragments,
                                    SettingsEditorFragment<ApplicationConfiguration, ModuleClasspathCombo> moduleClasspath,
                                    CommonParameterFragments<ApplicationConfiguration> commonParameterFragments) {
    // Create an additional entry under "Modify options", under the "Java" section
    fragments.add(SettingsEditorFragment.createTag("include.provided",
                                                   ExecutionBundle.message("application.configuration.include.provided.scope"),
                                                   ExecutionBundle.message("group.java.options"),
                                                   configuration -> configuration.getOptions().isIncludeProvidedScope(),
                                                   (configuration, value) -> configuration.getOptions().setIncludeProvidedScope(value)));

    // Create an additional entry under "Modify options", under the "Java" section
    fragments.add(SettingsEditorFragment.createTag("unnamed.class",
                                                   ExecutionBundle.message("application.configuration.is.implicit.class"),
                                                   ExecutionBundle.message("group.java.options"),
                                                   configuration -> {
                                                     // Called only on the initial display of the run configuration.
                                                     // Controls whether to display this TagButton or not.
                                                     return configuration.isImplicitClassConfiguration();
                                                   },
                                                   (configuration, value) -> {
                                                     configuration.setImplicitClassConfiguration(value);
                                                     updateMainClassFragment(configuration.isImplicitClassConfiguration());
                                                   }));
    fragments.add(commonParameterFragments.programArguments());
    fragments.add(new TargetPathFragment<>());
    fragments.add(commonParameterFragments.createRedirectFragment());
    SettingsEditorFragment<ApplicationConfiguration, MainClassPanel> mainClassFragment = createMainClass(moduleClasspath.component());
    fragments.add(mainClassFragment);
    DefaultJreSelector jreSelector = DefaultJreSelector.fromSourceRootsDependencies(
      moduleClasspath.component(), mainClassFragment.component().getEditorTextField()
    );
    SettingsEditorFragment<ApplicationConfiguration, JrePathEditor> jrePath = CommonJavaFragments.createJrePath(jreSelector);
    fragments.add(jrePath);
    fragments.add(createShortenClasspath(moduleClasspath.component(), jrePath, true));
  }

  private class MainClassPanel extends JPanel {
    private final ClassEditorField myClassEditorField;
    private final TextFieldWithAutoCompletion<String> myImplicitClassField;
    private boolean myIsImplicitClassConfiguration;

    private MainClassPanel(ModuleClasspathCombo classpathCombo) {
      super(new GridBagLayout());
      setMinimumWidth(this, 300);

      ConfigurationModuleSelector moduleSelector = new ConfigurationModuleSelector(getProject(), classpathCombo);
      myClassEditorField = ClassEditorField.createClassField(getProject(), () -> classpathCombo.getSelectedModule(),
                                                             ApplicationConfigurable.getVisibilityChecker(moduleSelector), null);
      myClassEditorField.setBackground(UIUtil.getTextFieldBackground());
      myClassEditorField.setShowPlaceholderWhenFocused(true);
      CommonParameterFragments.setMonospaced(myClassEditorField);
      String placeholder = ExecutionBundle.message("application.configuration.main.class.placeholder");
      myClassEditorField.setPlaceholder(placeholder);
      myClassEditorField.getAccessibleContext().setAccessibleName(placeholder);
      myClassEditorField.setVisible(!myIsImplicitClassConfiguration);
      setMinimumWidth(myClassEditorField, 300);
      GridBag constraints = new GridBag().setDefaultFill(GridBagConstraints.HORIZONTAL).setDefaultWeightX(1.0);
      add(myClassEditorField, constraints.nextLine());

      myImplicitClassField = new TextFieldWithAutoCompletion<>(getProject(), new StringsCompletionProvider(null, AllIcons.FileTypes.Java) {
        @Override
        public @NotNull Collection<String> getItems(String prefix, boolean cached, CompletionParameters parameters) {
          return DumbService.isDumb(getProject())
                 ? List.of()
                 : ReadAction.compute(() -> StubIndex.getInstance().getAllKeys(JavaStubIndexKeys.IMPLICIT_CLASSES, getProject()));
        }
      }, true, null);
      CommonParameterFragments.setMonospaced(myImplicitClassField);
      String implicitClassPlaceholder = ExecutionBundle.message("application.configuration.main.unnamed.class.placeholder");
      myImplicitClassField.setVisible(myIsImplicitClassConfiguration);
      myImplicitClassField.setPlaceholder(implicitClassPlaceholder);
      myImplicitClassField.getAccessibleContext().setAccessibleName(implicitClassPlaceholder);
      setMinimumWidth(myImplicitClassField, 300);
      add(myImplicitClassField, constraints.nextLine());
    }

    public EditorTextField getEditorTextField() { return myClassEditorField; }

    void setClassName(String name) {
      if (myIsImplicitClassConfiguration) {
        myImplicitClassField.setText(name);
      }
      else {
        myClassEditorField.setClassName(name);
      }
    }

    String getClassName() {
      return myIsImplicitClassConfiguration ? myImplicitClassField.getText() : myClassEditorField.getClassName();
    }

    boolean isReadyForApply() {
      return myIsImplicitClassConfiguration || myClassEditorField.isReadyForApply();
    }

    void setImplicitClassConfiguration(boolean isImplicitClassConfiguration) {
      myIsImplicitClassConfiguration = isImplicitClassConfiguration;
      if (myClassEditorField != null) {
        myClassEditorField.setVisible(!isImplicitClassConfiguration);
        myImplicitClassField.setVisible(isImplicitClassConfiguration);
      }
    }

    List<ValidationInfo> getValidation(ApplicationConfiguration configuration) {
      return Collections.singletonList(RuntimeConfigurationException.validate(
        myIsImplicitClassConfiguration ? myImplicitClassField : myClassEditorField,
        () -> {
          if (!isDefaultSettings()) configuration.checkClass();
        }
      ));
    }

    JComponent getEditorComponent() {
      if (myIsImplicitClassConfiguration) {
        return myImplicitClassField;
      }
      else {
        Editor editor = myClassEditorField.getEditor();
        return editor == null ? myClassEditorField : editor.getContentComponent();
      }
    }

    @Override
    public synchronized void addMouseListener(MouseListener l) {
      myImplicitClassField.addMouseListener(l);
      myClassEditorField.addMouseListener(l);
    }
  }

  private @NotNull SettingsEditorFragment<ApplicationConfiguration, MainClassPanel> createMainClass(ModuleClasspathCombo classpathCombo) {
    final var mainClassPanel = new MainClassPanel(classpathCombo);
    myMainClassFragment =
      new SettingsEditorFragment<>("mainClass", ExecutionBundle.message("application.configuration.main.class"), null, mainClassPanel, 20,
                                   (configuration, component) -> mainClassPanel.setClassName(configuration.getMainClassName()),
                                   (configuration, component) -> configuration.setMainClassName(mainClassPanel.getClassName()),
                                   Predicates.alwaysTrue()) {
        @Override
        public boolean isReadyForApply() {
          return myComponent.isReadyForApply();
        }
      };
    myMainClassFragment.setRemovable(false);
    myMainClassFragment.setEditorGetter(field -> field.getEditorComponent());
    myMainClassFragment.setValidation((configuration) -> mainClassPanel.getValidation(configuration));
    updateMainClassFragment(myInitialIsImplicitClass);
    return myMainClassFragment;
  }

  private void updateMainClassFragment(boolean isImplicitClass) {
    if (myMainClassFragment == null) return;
    myMainClassFragment.component().setImplicitClassConfiguration(isImplicitClass);

    if (isImplicitClass) {
      myMainClassFragment.setHint(ExecutionBundle.message("application.configuration.main.class.unnamed.hint"));
    }
    else {
      myMainClassFragment.setHint(ExecutionBundle.message("application.configuration.main.class.hint"));
    }
  }
}
