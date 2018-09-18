// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import org.jdom.Element;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@State(name = "EntryPointsManager")
public class EntryPointsManagerImpl extends EntryPointsManagerBase implements PersistentStateComponent<Element> {
  public EntryPointsManagerImpl(Project project) {
    super(project);
  }

  @Override
  public void configureAnnotations() {
    final List<String> list = new ArrayList<>(ADDITIONAL_ANNOTATIONS);
    final List<String> writeList = new ArrayList<>(myWriteAnnotations);

    final JPanel listPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(list, "Mark as entry point if annotated by", true);
    Condition<PsiClass> applicableToField = psiClass -> {
      Set<PsiAnnotation.TargetType> annotationTargets = AnnotationTargetUtil.getAnnotationTargets(psiClass);
      return annotationTargets != null && annotationTargets.contains(PsiAnnotation.TargetType.FIELD);
    };
    final JPanel writtenAnnotationsPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(writeList, "Mark field as implicitly written if annotated by", false, applicableToField);
    new DialogWrapper(myProject) {
      {
        init();
        setTitle("Configure Annotations");
      }

      @Override
      protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new VerticalFlowLayout());
        panel.add(listPanel);
        panel.add(writtenAnnotationsPanel);
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
    final JButton configureAnnotations = new JButton("Annotations...");
    configureAnnotations.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getInstance(ProjectUtil.guessCurrentProject(configureAnnotations)).configureAnnotations();
      }
    });
    return configureAnnotations;
  }

  public static JButton createConfigureClassPatternsButton() {
    final JButton configureClassPatterns = new JButton("Code patterns...");
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
            setTitle("Configure Code Patterns");
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