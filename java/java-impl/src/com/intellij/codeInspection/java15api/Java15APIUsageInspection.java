/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.java15api;

import com.intellij.ExtensionPoints;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * @author max
 */
public class Java15APIUsageInspection extends BaseJavaLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "Since15";

  private static final Map<LanguageLevel, Set<String>> ourForbiddenAPI = new EnumMap<LanguageLevel, Set<String>>(LanguageLevel.class);
  private static final Set<String> ourIgnored16ClassesAPI = new THashSet<String>(10);
  private static final Map<LanguageLevel, String> ourAPIPresentationMap = new EnumMap<LanguageLevel, String>(LanguageLevel.class);

  private LanguageLevel myEffectiveLanguageLevel = null;

  static {
    final THashSet<String> ourForbidden14API = new THashSet<String>(1000);
    initForbiddenApi("api14List.txt", ourForbidden14API);
    ourForbiddenAPI.put(LanguageLevel.JDK_1_3, ourForbidden14API);
    ourAPIPresentationMap.put(LanguageLevel.JDK_1_3, "1.4");
    final THashSet<String> ourForbidden15API = new THashSet<String>(1000);
    initForbiddenApi("apiList.txt", ourForbidden15API);
    ourForbiddenAPI.put(LanguageLevel.JDK_1_4, ourForbidden15API);
    ourAPIPresentationMap.put(LanguageLevel.JDK_1_4, "1.5");
    final THashSet<String> ourForbidden16API = new THashSet<String>(1000);
    initForbiddenApi("api16List.txt", ourForbidden16API);
    ourForbiddenAPI.put(LanguageLevel.JDK_1_5, ourForbidden16API);
    ourAPIPresentationMap.put(LanguageLevel.JDK_1_5, "1.6");
    initForbiddenApi("ignore16List.txt", ourIgnored16ClassesAPI);
  }

  private static void initForbiddenApi(@NonNls String list, Set<String> set) {
    BufferedReader reader = null;
    try {
      final InputStream stream = Java15APIUsageInspection.class.getResourceAsStream(list);
      reader = new BufferedReader(new InputStreamReader(stream, CharsetToolkit.UTF8_CHARSET));

      do {
        String line = reader.readLine();
        if (line == null) break;

        set.add(line);
      } while(true);
    }
    catch (UnsupportedEncodingException e) {
      // can't be.
    }
    catch (IOException e) {
      // can't be
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException e) {
          // Will not happen
        }
      }
    }
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.1.5.display.name", "@since 1.5(1.6)");
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }


  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public void readSettings(Element node) throws InvalidDataException {
    final Element element = node.getChild(EFFECTIVE_LL);
    if (element != null) {
      myEffectiveLanguageLevel = LanguageLevel.valueOf(element.getAttributeValue("value"));
    }
  }

  @Override
  public void writeSettings(Element node) throws WriteExternalException {
    if (myEffectiveLanguageLevel != null) {
      final Element llElement = new Element(EFFECTIVE_LL);
      llElement.setAttribute("value", myEffectiveLanguageLevel.toString());
      node.addContent(llElement);
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    panel.add(new JLabel("Forbid API usages:"));

    final JRadioButton projectRb = new JRadioButton("Respecting to project language level settings");
    panel.add(projectRb);
    final JRadioButton customRb = new JRadioButton("Higher than:");
    panel.add(customRb);
    final ButtonGroup gr = new ButtonGroup();
    gr.add(projectRb);
    gr.add(customRb);


    final DefaultComboBoxModel cModel = new DefaultComboBoxModel();
    final JComboBox llCombo = new JComboBox(cModel){
      @Override
      public void setEnabled(boolean b) {
        if (b == customRb.isSelected()) {
          super.setEnabled(b);
        }
      }
    };
    for (LanguageLevel level : LanguageLevel.values()) {
      cModel.addElement(level);
    }
    llCombo.setSelectedItem(myEffectiveLanguageLevel != null ? myEffectiveLanguageLevel : LanguageLevel.JDK_1_3);
    llCombo.setRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof LanguageLevel) {
          setText(((LanguageLevel)value).getPresentableText());
        }
        return rendererComponent;
      }
    });
    llCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myEffectiveLanguageLevel = (LanguageLevel)llCombo.getSelectedItem();
      }
    });
    final JPanel comboPanel = new JPanel(new BorderLayout());
    comboPanel.setBorder(BorderFactory.createEmptyBorder(5, 20, 5, 5));
    comboPanel.add(llCombo, BorderLayout.WEST);
    panel.add(comboPanel);

    final ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (projectRb.isSelected()) {
          myEffectiveLanguageLevel = null;
        } else {
          myEffectiveLanguageLevel = (LanguageLevel)llCombo.getSelectedItem();
        }
        UIUtil.setEnabled(comboPanel, !projectRb.isSelected(), true);
      }
    };
    projectRb.addActionListener(actionListener);
    customRb.addActionListener(actionListener);
    projectRb.setSelected(myEffectiveLanguageLevel == null);
    customRb.setSelected(myEffectiveLanguageLevel != null);
    UIUtil.setEnabled(comboPanel, !projectRb.isSelected(), true);
    return panel;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new MyVisitor(holder);
  }

  private static boolean isInProject(final PsiElement elt) {
    return elt.getManager().isInProject(elt);
  }

  @Override @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    ExtensionPoint<FileCheckingInspection> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.JAVA15_INSPECTION_TOOL);
    final FileCheckingInspection[] fileCheckingInspections = point.getExtensions();
    for(FileCheckingInspection obj: fileCheckingInspections) {
      ProblemDescriptor[] descriptors = obj.checkFile(file, manager, isOnTheFly);
      if (descriptors != null) {
        return descriptors;
      }
    }

    return null;
  }

  public static String getPresentable(LanguageLevel languageLevel) {
    return ourAPIPresentationMap.get(languageLevel);
  }

  private static class MyVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public MyVisitor(final ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override public void visitDocComment(PsiDocComment comment) {
      // No references inside doc comment are of interest.
    }

    @Override public void visitClass(PsiClass aClass) {
      // Don't go into classes (anonymous, locals).
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement resolved = reference.resolve();

      if (resolved instanceof PsiCompiledElement && resolved instanceof PsiMember) {
        final Module module = ModuleUtil.findModuleForPsiElement(reference.getElement());
        if (module != null) {
          final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
          if (isForbiddenApiUsage((PsiMember)resolved, languageLevel)) {
            PsiClass psiClass = null;
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier != null) {
              if (qualifier instanceof PsiExpression) {
                psiClass = PsiUtil.resolveClassInType(((PsiExpression)qualifier).getType());
              }
            }
            else {
              psiClass = PsiTreeUtil.getParentOfType(reference, PsiClass.class);
            }
            if (psiClass != null) {
              if (isIgnored(psiClass)) return;
              for (PsiClass superClass : psiClass.getSupers()) {
                if (isIgnored(superClass)) return;
              }
            }
            registerError(reference, languageLevel);
          }
        }
      }
    }

    private static boolean isIgnored(PsiClass psiClass) {
      final String qualifiedName = psiClass.getQualifiedName();
      return qualifiedName != null && ourIgnored16ClassesAPI.contains(qualifiedName);
    }

    @Override public void visitNewExpression(final PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiMethod constructor = expression.resolveConstructor();
      final Module module = ModuleUtil.findModuleForPsiElement(expression);
      if (module != null) {
        final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
        if (constructor instanceof PsiCompiledElement) {
          if (isForbiddenApiUsage(constructor, languageLevel)) {
            registerError(expression.getClassReference(), languageLevel);
          }
        }
      }
    }

    private LanguageLevel getEffectiveLanguageLevel(Module module) {
      if (myEffectiveLanguageLevel != null) return myEffectiveLanguageLevel;
      return LanguageLevelUtil.getEffectiveLanguageLevel(module);
    }

    private void registerError(PsiJavaCodeReferenceElement reference, LanguageLevel api) {
      if (isInProject(reference)) {
        myHolder.registerProblem(reference, InspectionsBundle.message("inspection.1.5.problem.descriptor", getPresentable(api)));
      }
    }
  }

  public static boolean isForbiddenApiUsage(final PsiMember member, LanguageLevel languageLevel) {
    if (member == null) return false;

    // Annotations caught by special inspection if necessary
    if (member instanceof PsiClass && ((PsiClass)member).isAnnotationType()) return false;

    if (member instanceof PsiAnonymousClass) return false;
    if (member.getContainingClass() instanceof PsiAnonymousClass) return false;
    if (member instanceof PsiClass && !(member.getParent() instanceof PsiClass || member.getParent() instanceof PsiFile)) return false;

    return isForbiddenSignature(member, languageLevel) ||
           isForbiddenApiUsage(member.getContainingClass(), languageLevel);

  }

  private static boolean isForbiddenSignature(PsiMember member, LanguageLevel languageLevel) {
    Set<String> forbiddenApi = ourForbiddenAPI.get(languageLevel);
    if (forbiddenApi == null) return false;
    return forbiddenApi.contains(getSignature(member)) ||
           languageLevel.compareTo(LanguageLevel.HIGHEST) != 0 && isForbiddenSignature(member, LanguageLevel.values()[languageLevel.ordinal() + 1]);
  }

  public static String getSignature(PsiMember member) {
    if (member instanceof PsiClass) {
      return ((PsiClass)member).getQualifiedName();
    }
    if (member instanceof PsiField) {
      return getSignature(member.getContainingClass()) + "#" + member.getName();
    }
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      StringBuffer buf = new StringBuffer();
      buf.append(getSignature(method.getContainingClass()));
      buf.append('#');
      buf.append(method.getName());
      buf.append('(');
      final PsiType[] params = method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes();
      for (PsiType type : params) {
        buf.append(type.getCanonicalText());
        buf.append(";");
      }
      buf.append(')');
      return buf.toString();
    }
    assert false;
    return null;
  }

}
