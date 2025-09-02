// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex.impl;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.util.ui.JBInsets;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@State(name = "EntryPointsManager")
public final class EntryPointsManagerImpl extends EntryPointsManagerBase implements PersistentStateComponent<Element> {
  public EntryPointsManagerImpl(Project project) {
    super(project);
  }

  @Override
  public void configureAnnotations() {
    configureAnnotations(false);
  }
  
  @Override
  public void configureAnnotations(boolean implicitWritesOnly) {
    List<String> list = new ArrayList<>(ADDITIONAL_ANNOTATIONS);
    List<String> writeList = new ArrayList<>(myWriteAnnotations);

    JPanel listPanel;
    if (implicitWritesOnly) {
      listPanel = null;
    }
    else {
      listPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
        list, JavaBundle.message("separator.mark.as.entry.point.if.annotated.by"), true);
    }
    Predicate<PsiClass> applicableToField = psiClass -> {
      Set<PsiAnnotation.TargetType> annotationTargets = AnnotationTargetUtil.getAnnotationTargets(psiClass);
      return annotationTargets != null && annotationTargets.contains(PsiAnnotation.TargetType.FIELD);
    };
    JPanel writtenAnnotationsPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      writeList, JavaBundle.message("separator.mark.field.as.implicitly.written.if.annotated.by"), false, applicableToField);
    new DialogWrapper(myProject) {
      {
        init();
        setTitle(JavaBundle.message("dialog.title.configure.annotations"));
      }

      @Override
      protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        var constraints = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1,
                                                       GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                       JBInsets.emptyInsets(), 0, 0);
        if (listPanel != null) {
          panel.add(listPanel, constraints);
          constraints.insets.top = 13;
        }
        panel.add(writtenAnnotationsPanel, constraints);
        return panel;
      }

      @Override
      protected void doOKAction() {
        ADDITIONAL_ANNOTATIONS.clear();
        ADDITIONAL_ANNOTATIONS.addAll(list);

        myWriteAnnotations.clear();
        myWriteAnnotations.addAll(writeList);

        DaemonCodeAnalyzerEx.getInstanceEx(myProject).restart("EntryPointsManagerImpl.configureAnnotations");
        super.doOKAction();
      }
    }.show();
  }

  public static JButton createConfigureAnnotationsButton(Project project, boolean implicitWritesOnly) {
    JButton configureAnnotations = new JButton(JavaBundle.message("button.annotations"));
    configureAnnotations.addActionListener(__ -> getInstance(project).configureAnnotations(implicitWritesOnly));
    return configureAnnotations;
  }

  public static JButton createConfigureClassPatternsButton(Project project) {
    JButton configureClassPatterns = new JButton(JavaBundle.message("button.code.patterns"));
    configureClassPatterns.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        EntryPointsManagerBase entryPointsManagerBase = getInstance(project);
        ArrayList<ClassPattern> list = new ArrayList<>();
        for (ClassPattern pattern : entryPointsManagerBase.getPatterns()) {
          list.add(new ClassPattern(pattern));
        }
        ClassPatternsPanel panel = new ClassPatternsPanel(list);
        new DialogWrapper(project) {

          {
            init();
            setTitle(JavaBundle.message("dialog.title.configure.code.patterns"));
          }

          @Override
          protected JComponent createCenterPanel() {
            return panel;
          }

          @Override
          protected void doOKAction() {
            String error = panel.getValidationError(project);
            if (error != null) {
              Messages.showErrorDialog(panel, error);
              return;
            }
            Set<ClassPattern> patterns = entryPointsManagerBase.getPatterns();
            patterns.clear();
            patterns.addAll(list);
            DaemonCodeAnalyzerEx.getInstanceEx(project).restart("EntryPointsManagerImpl.createConfigureClassPatternsButton");
            super.doOKAction();
          }
        }.show();
      }
    });
    return configureClassPatterns;
  }
}