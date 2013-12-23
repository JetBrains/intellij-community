/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class LayoutProjectCodeDialog extends DialogWrapper implements ReformatFilesOptions {
  private static @NonNls final String HELP_ID = "editing.codeReformatting";

  private final String  myText;
  private final boolean mySuggestOptimizeImports;
  private final Project myProject;
  private final Module  myModule;
  
  private JCheckBox myCbOptimizeImports;
  private JCheckBox myCbOnlyVcsChangedRegions;
  private JCheckBox myCbRearrangeEntries;

  public LayoutProjectCodeDialog(@NotNull Project project,
                                 @Nullable Module module,
                                 String title,
                                 String text,
                                 boolean suggestOptimizeImports)
  {
    super(project, false);
    myText = text;
    mySuggestOptimizeImports = suggestOptimizeImports;
    myProject = project;
    myModule = module;
    
    setOKButtonText(CodeInsightBundle.message("reformat.code.accept.button.text"));
    setTitle(title);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    if (!mySuggestOptimizeImports) return new JLabel(myText);
    JPanel panel = new JPanel(new GridLayout(4, 1));
    panel.add(new JLabel(myText));
    myCbOptimizeImports = new JCheckBox(CodeInsightBundle.message("reformat.option.optimize.imports"));
    panel.add(myCbOptimizeImports);
    myCbOptimizeImports.setSelected(PropertiesComponent.getInstance().getBoolean(LayoutCodeConstants.OPTIMIZE_IMPORTS_KEY, false));

    myCbOnlyVcsChangedRegions = new JCheckBox(CodeInsightBundle.message("reformat.option.vcs.changed.region"));
    panel.add(myCbOnlyVcsChangedRegions);
    final boolean canTargetVcsRegions = canTargetVcsRegions();
    myCbOnlyVcsChangedRegions.setEnabled(canTargetVcsRegions);
    myCbOnlyVcsChangedRegions.setSelected(
      canTargetVcsRegions && PropertiesComponent.getInstance().getBoolean(LayoutCodeConstants.PROCESS_CHANGED_TEXT_KEY, false)
    );


    myCbRearrangeEntries = new JCheckBox(CodeInsightBundle.message("reformat.option.rearrange.entries"));
    panel.add(myCbRearrangeEntries);
    boolean previousSelectedState = LayoutCodeSettingsStorage.getLastSavedRearrangeEntriesCbStateFor(myProject);
    myCbRearrangeEntries.setSelected(previousSelectedState);

    return panel;
  }
  
  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  public boolean isRearrangeEntries() {
    return myCbRearrangeEntries.isSelected();
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    if (mySuggestOptimizeImports) {
      PropertiesComponent.getInstance().setValue(LayoutCodeConstants.OPTIMIZE_IMPORTS_KEY, Boolean.toString(isOptimizeImports()));
    }
    LayoutCodeSettingsStorage.saveRearrangeEntriesOptionFor(myProject, isRearrangeEntries());
  }

  public boolean isOptimizeImports() {
    return myCbOptimizeImports.isSelected();
  }

  public boolean isProcessOnlyChangedText() {
    return myCbOnlyVcsChangedRegions.isEnabled() && myCbOnlyVcsChangedRegions.isSelected();
  }
  
  private boolean canTargetVcsRegions() {
    if (myModule != null) {
      return FormatChangedTextUtil.hasChanges(myModule);
    }

    return FormatChangedTextUtil.hasChanges(myProject);
  }
}
