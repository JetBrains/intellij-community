// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@State(name = "EntryPointsManager")
public class EntryPointsManagerImpl extends EntryPointsManagerBase implements PersistentStateComponent<Element> {
  public EntryPointsManagerImpl(Project project) {
    super(project);
  }

  @Override
  public void configureAnnotations() {
    final List<String> list = new ArrayList<>(ADDITIONAL_ANNOTATIONS);
    final List<String> writeList = new ArrayList<>(myWriteAnnotations);

    final JPanel listPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      list, JavaBundle.message("separator.mark.as.entry.point.if.annotated.by"), true);
    Predicate<PsiClass> applicableToField = psiClass -> {
      Set<PsiAnnotation.TargetType> annotationTargets = AnnotationTargetUtil.getAnnotationTargets(psiClass);
      return annotationTargets != null && annotationTargets.contains(PsiAnnotation.TargetType.FIELD);
    };
    final JPanel writtenAnnotationsPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      writeList, JavaBundle.message("separator.mark.field.as.implicitly.written.if.annotated.by"), false, applicableToField);
    new DialogWrapper(myProject) {
      {
        init();
        setTitle(JavaBundle.message("dialog.title.configure.annotations"));
      }

      @Override
      protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final var constraints = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1,
                                                       GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                       JBInsets.emptyInsets(), 0, 0);
        panel.add(listPanel, constraints);
        constraints.insets.top = 13;
        panel.add(writtenAnnotationsPanel, constraints);
        return panel;
      }

      @Override
      protected void doOKAction() {
        ADDITIONAL_ANNOTATIONS.clear();
        ADDITIONAL_ANNOTATIONS.addAll(list);

        myWriteAnnotations.clear();
        myWriteAnnotations.addAll(writeList);

        DaemonCodeAnalyzer.getInstance(myProject).restart();
        super.doOKAction();
      }
    }.show();
  }

  public static JButton createConfigureAnnotationsButton() {
    final JButton configureAnnotations = new JButton(JavaBundle.message("button.annotations"));
    configureAnnotations.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getInstance(ProjectUtil.guessCurrentProject(configureAnnotations)).configureAnnotations();
      }
    });
    return configureAnnotations;
  }

  public static JButton createConfigureClassPatternsButton() {
    final JButton configureClassPatterns = new JButton(JavaBundle.message("button.code.patterns"));
    configureClassPatterns.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final Project project = ProjectUtil.guessCurrentProject(configureClassPatterns);
        final EntryPointsManagerBase entryPointsManagerBase = getInstance(project);
        final ArrayList<ClassPattern> list = new ArrayList<>();
        for (ClassPattern pattern : entryPointsManagerBase.getPatterns()) {
          list.add(new ClassPattern(pattern));
        }
        final ClassPatternsPanel panel = new ClassPatternsPanel(list);
        new DialogWrapper(entryPointsManagerBase.myProject) {

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
            final String error = panel.getValidationError(project);
            if (error != null) {
              Messages.showErrorDialog(panel, error);
              return;
            }
            final LinkedHashSet<ClassPattern> patterns = entryPointsManagerBase.getPatterns();
            patterns.clear();
            patterns.addAll(list);
            DaemonCodeAnalyzer.getInstance(entryPointsManagerBase.myProject).restart();
            super.doOKAction();
          }
        }.show();
      }
    });
    return configureClassPatterns;
  }
}