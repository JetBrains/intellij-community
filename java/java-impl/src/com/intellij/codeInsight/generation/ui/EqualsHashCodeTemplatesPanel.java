// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.ui;

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManagerBase;
import com.intellij.codeInsight.generation.GenerateEqualsHelper;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedItemsListEditor;
import com.intellij.openapi.ui.Namer;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiType;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.view.GenerateTemplateConfigurable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

public class EqualsHashCodeTemplatesPanel extends NamedItemsListEditor<Couple<TemplateResource>> {
  private static final Namer<Couple<TemplateResource>> NAMER = new Namer<>() {

    @Override
    public String getName(Couple<TemplateResource> couple) {
      return EqualsHashCodeTemplatesManagerBase.getTemplateBaseName(couple.first);
    }

    @Override
    public boolean canRename(Couple<TemplateResource> item) {
      return !item.first.isDefault();
    }

    @Override
    public void setName(Couple<TemplateResource> couple, String name) {
      couple.first.setFileName(EqualsHashCodeTemplatesManagerBase.toEqualsName(name));
      couple.second.setFileName(EqualsHashCodeTemplatesManagerBase.toHashCodeName(name));
    }
  };

  private static final Factory<Couple<TemplateResource>> FACTORY = () -> Couple.of(new TemplateResource(), new TemplateResource());

  private static final Cloner<Couple<TemplateResource>> CLONER = new Cloner<>() {
    @Override
    public Couple<TemplateResource> cloneOf(Couple<TemplateResource> couple) {
      if (couple.first.isDefault()) return couple;
      return copyOf(couple);
    }

    @Override
    public Couple<TemplateResource> copyOf(Couple<TemplateResource> couple) {
      return Couple.of(copyOf(couple.first), copyOf(couple.second));
    }

    private static @NotNull TemplateResource copyOf(TemplateResource resource) {
      TemplateResource result = new TemplateResource();
      result.setFileName(resource.getFileName());
      result.setTemplate(resource.getTemplate());
      return result;
    }
  };

  private static final BiPredicate<Pair<TemplateResource, TemplateResource>, Pair<TemplateResource, TemplateResource>> COMPARER =
    new BiPredicate<>() {
      @Override
      public boolean test(Pair<TemplateResource, TemplateResource> o1, Pair<TemplateResource, TemplateResource> o2) {
        return equals(o1.first, o2.first) && equals(o1.second, o2.second);
      }

      private static boolean equals(TemplateResource r1, TemplateResource r2) {
        return Objects.equals(r1.getTemplate(), r2.getTemplate()) && Objects.equals(r1.getFileName(), r2.getFileName());
      }
    };
  private final Project myProject;
  private final EqualsHashCodeTemplatesManagerBase myManager;

  public EqualsHashCodeTemplatesPanel(Project project, EqualsHashCodeTemplatesManagerBase manager) {
    super(NAMER, FACTORY, CLONER, COMPARER, new ArrayList<>(manager.getTemplateCouples()));
    myProject = project;
    myManager = manager;
  }

  @Override
  public @Nls String getDisplayName() {
    return JavaBundle.message("configurable.EqualsHashCodeTemplatesPanel.display.name");
  }

  @Override
  protected String getCopyDialogTitle() {
    return JavaBundle.message("dialog.title.copy.template");
  }

  @Override
  protected String getCreateNewDialogTitle() {
    return JavaBundle.message("dialog.title.create.new.template");
  }

  @Override
  protected @NlsContexts.Label String getNewLabelText() {
    return JavaBundle.message("label.new.template.name");
  }

  @Override
  public @Nullable @NonNls String getHelpTopic() {
    return null;
  }

  @Override
  public boolean isModified() {
    return super.isModified() || !Comparing.equal(myManager.getDefaultTemplate(), getSelectedItem().first);
  }

  @Override
  protected boolean canDelete(Couple<TemplateResource> item) {
    return !item.first.isDefault();
  }

  @Override
  protected UnnamedConfigurable createConfigurable(Couple<TemplateResource> item) {
    final GenerateTemplateConfigurable equalsConfigurable = new GenerateTemplateConfigurable(item.first, getEqualsImplicitVars(), myProject);
    final GenerateTemplateConfigurable hashCodeConfigurable = new GenerateTemplateConfigurable(item.second, getHashCodeImplicitVars(), myProject);
    return new UnnamedConfigurable() {
      @Override
      public @NotNull JComponent createComponent() {
        final Splitter splitter = new Splitter(true);

        final JPanel eqPanel = new JPanel(new BorderLayout());
        eqPanel.add(new TitledSeparator(JavaBundle.message("generate.equals.template.title")), BorderLayout.NORTH);
        final JComponent eqPane = equalsConfigurable.createComponent();
        eqPane.setPreferredSize(JBUI.size(300, 200));
        eqPanel.add(eqPane, BorderLayout.CENTER);
        splitter.setFirstComponent(eqPanel);

        final JPanel hcPanel = new JPanel(new BorderLayout());
        hcPanel.add(new TitledSeparator(JavaBundle.message("generate.hashcode.template.title")), BorderLayout.NORTH);
        final JComponent hcPane = hashCodeConfigurable.createComponent();
        hcPane.setPreferredSize(JBUI.size(300, 200));
        hcPanel.add(hcPane, BorderLayout.CENTER);
        splitter.setSecondComponent(hcPanel);

        return splitter;
      }

      @Override
      public boolean isModified() {
        return equalsConfigurable.isModified() || hashCodeConfigurable.isModified();
      }

      @Override
      public void apply() throws ConfigurationException {
        equalsConfigurable.apply();
        hashCodeConfigurable.apply();
      }

      @Override
      public void reset() {
        equalsConfigurable.reset();
        hashCodeConfigurable.reset();
      }

      @Override
      public void disposeUIResources() {
        equalsConfigurable.disposeUIResources();
        hashCodeConfigurable.disposeUIResources();
      }
    };
  }

  protected @NotNull Map<String, PsiType> getHashCodeImplicitVars() {
    return GenerateEqualsHelper.getHashCodeImplicitVars();
  }

  protected @NotNull Map<String, PsiType> getEqualsImplicitVars() {
    return GenerateEqualsHelper.getEqualsImplicitVars(myProject);
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    List<TemplateResource> resources = new ArrayList<>();
    for (Couple<TemplateResource> resource : getItems()) {
      resources.add(resource.first);
      resources.add(resource.second);
    }
    myManager.setTemplates(resources);

    final Couple<TemplateResource> selection = getSelectedItem();
    if (selection != null) {
      myManager.setDefaultTemplate(selection.first);
    }
  }
}

