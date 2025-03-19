// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate.view;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiType;
import com.intellij.util.LocalTimeCounter;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.element.ClassElement;
import org.jetbrains.java.generate.element.FieldElement;
import org.jetbrains.java.generate.element.GenerationHelper;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class GenerateTemplateConfigurable implements UnnamedConfigurable{
  private final TemplateResource template;
  private final Editor myEditor;
  private final List<String> availableImplicits = new ArrayList<>();
  private @Nls String myHint;

  public GenerateTemplateConfigurable(TemplateResource template, Map<String, PsiType> contextMap, Project project) {
    this(template, contextMap, project, true);
  }

  public GenerateTemplateConfigurable(TemplateResource template, Map<String, PsiType> contextMap, Project project, boolean multipleFields) {
      this.template = template;
      final EditorFactory factory = EditorFactory.getInstance();
      Document doc = factory.createDocument(template.getTemplate());
      final FileType ftl = FileTypeManager.getInstance().findFileTypeByName("VTL");
      if (project != null && ftl != null) {
        Document document = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> {
          final PsiFile file = PsiFileFactory.getInstance(project)
            .createFileFromText(template.getFileName(), ftl, template.getTemplate(), LocalTimeCounter.currentTime(), true);
          if (!template.isDefault()) {
            final HashMap<String, PsiType> map = new LinkedHashMap<>();
            map.put("class", TemplatesManager.createElementType(project, ClassElement.class));
            if (multipleFields) {
              map.put("fields", TemplatesManager.createFieldListElementType(project));
            }
            else {
              map.put("field", TemplatesManager.createElementType(project, FieldElement.class));
            }
            map.put("helper", TemplatesManager.createElementType(project, GenerationHelper.class));
            map.putAll(contextMap);
            availableImplicits.addAll(map.keySet());
            file.getViewProvider().putUserData(TemplatesManager.TEMPLATE_IMPLICITS, map);
          }
          return PsiDocumentManager.getInstance(project).getDocument(file);
        });
        if (document != null) {
          doc = document;
        }
      }
      myEditor = factory.createEditor(doc, project, ftl != null ? ftl : FileTypes.PLAIN_TEXT, template.isDefault());
    }

    public void setHint(@Nls String hint) {
      myHint = hint;
    }

    @Override
    public @NotNull JComponent createComponent() {
      final JComponent component = myEditor.getComponent();
      if (availableImplicits.isEmpty() && myHint == null) {
        return component;
      }
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(component, BorderLayout.CENTER);
      String availableVariables = JavaBundle.message("generate.tostring.available.implicit.variables.label", StringUtil.join(availableImplicits, ", "));
      JLabel label =
        new JLabel(XmlStringUtil.wrapInHtml(
          (!availableImplicits.isEmpty() ? availableVariables + "<br/>" : "") +
          (myHint != null ? myHint : "")));
      panel.add(label, BorderLayout.SOUTH);
      return panel;
    }

    @Override
    public boolean isModified() {
      return !Objects.equals(myEditor.getDocument().getText(), template.getTemplate());
    }

    @Override
    public void apply() throws ConfigurationException {
        template.setTemplate(myEditor.getDocument().getText());
    }

    @Override
    public void reset() {
      WriteCommandAction.writeCommandAction(null).run(() -> myEditor.getDocument().setText(template.getTemplate()));
    }

    @Override
    public void disposeUIResources() {
        EditorFactory.getInstance().releaseEditor(myEditor);
    }
}