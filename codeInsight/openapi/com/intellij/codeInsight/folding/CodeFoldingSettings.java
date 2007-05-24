package com.intellij.codeInsight.folding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

public abstract class CodeFoldingSettings implements ApplicationComponent {

  public static CodeFoldingSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(CodeFoldingSettings.class);
  }

  public abstract boolean isCollapseImports();
  public abstract void setCollapseImports(boolean value);

  public abstract boolean isCollapseMethods();
  public abstract void setCollapseMethods(boolean value);

  public abstract boolean isCollapseAccessors();
  public abstract void setCollapseAccessors(boolean value);

  public abstract boolean isCollapseInnerClasses();
  public abstract void setCollapseInnerClasses(boolean value);

  public abstract boolean isCollapseJavadocs();
  public abstract void setCollapseJavadocs(boolean value);

  public abstract boolean isCollapseXmlTags();
  public abstract void setCollapseXmlTags(boolean value);

  public abstract boolean isCollapseFileHeader();
  public abstract void setCollapseFileHeader(boolean value);

  public abstract boolean isCollapseAnonymousClasses();
  public abstract void setCollapseAnonymousClasses(boolean value);

  public abstract boolean isCollapseAnnotations();
  public abstract void setCollapseAnnotations(boolean value);

  @NotNull
  public String getComponentName() {
    return "CodeFoldingSettings";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

}
