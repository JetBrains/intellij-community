// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.find.findUsages.JavaFindUsagesCollector.FIND_THROW_STARTED;

public class FindThrowUsagesDialog extends JavaFindUsagesDialog<JavaThrowFindUsagesOptions> {
  private StateRestoringCheckBox myCbUsages;
  private JComboBox myCbExns;
  private boolean myHasFindWhatPanel;
  private ThrowSearchUtil.Root [] myRoots;

  public FindThrowUsagesDialog(@NotNull PsiElement element,
                               @NotNull Project project,
                               @NotNull JavaThrowFindUsagesOptions findUsagesOptions,
                               boolean toShowInNewTab,
                               boolean mustOpenInNewTab,
                               boolean isSingleFile,
                               @NotNull FindUsagesHandler handler) {
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  @Override
  protected void init () {
    // Kludge: myRoots used in super.init, which caller from constructor
    myRoots = ThrowSearchUtil.getSearchRoots(myPsiElement);
    super.init ();
  }

  @Override
  public JComponent getPreferredFocusedControl() {
    return myHasFindWhatPanel ? myCbUsages : null;
  }

  @Override
  protected JComponent createNorthPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBUI.insetsBottom(UIUtil.DEFAULT_VGAP);
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    myCbExns = new JComboBox (myRoots);
    panel.add(myCbExns, gbConstraints);

    return panel;
  }

  @Override
  public void calcFindUsagesOptions(final JavaThrowFindUsagesOptions options) {
    super.calcFindUsagesOptions(options);
    options.isUsages = isSelected(myCbUsages) || !myHasFindWhatPanel;
    FIND_THROW_STARTED.log(myPsiElement.getProject(), createFeatureUsageData(options));
  }

  @Override
  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(JavaBundle.message("find.what.usages.checkbox"), myFindUsagesOptions.isUsages, findWhatPanel, true);
    //final ThrowSearchUtil.Root[] searchRoots = ThrowSearchUtil.getSearchRoots(getPsiElement ());

    //final PsiThrowStatement throwStatement = (PsiThrowStatement)getPsiElement();
    //final boolean exactExnType = ThrowSearchUtil.isExactExnType(throwStatement.getException ());
    //if (exactExnType) {
    //  myCbStrict.setEnabled(false);
    //}
    myHasFindWhatPanel = true;
    return findWhatPanel;
  }

  @Override
  protected void doOKAction() {
    getFindUsagesOptions().setRoot((ThrowSearchUtil.Root)myCbExns.getSelectedItem());
    super.doOKAction();
  }

  @Override
  protected void update(){
    if (!myHasFindWhatPanel){
      setOKActionEnabled(true);
    }
    else{
      getFindUsagesOptions().setRoot((ThrowSearchUtil.Root)myCbExns.getSelectedItem());
      final boolean hasSelected = isSelected(myCbUsages);
      setOKActionEnabled(hasSelected);
    }
  }
}