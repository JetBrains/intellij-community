// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateParseException;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.OrderedSet;
import org.apache.velocity.runtime.parser.ParseException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public abstract class FileTemplateBase implements FileTemplate {
  static final boolean DEFAULT_REFORMAT_CODE_VALUE = true;
  static final boolean DEFAULT_ENABLED_VALUE = true;
  static final String TEMPLATE_CHILDREN_SUFFIX = ".child.";
  private @Nullable String myText;
  private boolean myShouldReformatCode = DEFAULT_REFORMAT_CODE_VALUE;
  private boolean myLiveTemplateEnabled;
  private boolean myLiveTemplateEnabledChanged;
  private String myFileName = "";
  private FileTemplate[] myChildren = EMPTY_ARRAY;

  @Override
  public final boolean isReformatCode() {
    return myShouldReformatCode;
  }

  @Override
  public final void setReformatCode(boolean reformat) {
    myShouldReformatCode = reformat;
  }

  public final @NotNull String getQualifiedName() {
    return getQualifiedName(getName(), getExtension());
  }

  public static @NotNull String getQualifiedName(@NonNls @NotNull String name, @NonNls @NotNull String extension) {
    return FTManager.encodeFileName(name, extension);
  }

  @Override
  public final @NotNull String getText() {
    final String text = myText;
    return text != null ? text : getDefaultText();
  }

  @Override
  public final void setText(@Nullable String text) {
    if (text == null) {
      myText = null;
    }
    else {
      final String converted = StringUtil.convertLineSeparators(text);
      myText = converted.equals(getDefaultText()) ? null : StringUtil.internEmptyString(converted);
    }
  }

  protected @NotNull String getDefaultText() {
    return "";
  }

  @Override
  public final @NotNull String getText(@NotNull Map attributes) throws IOException{
    return FileTemplateUtil.mergeTemplate(attributes, getText(), false);
  }

  @Override
  public final @NotNull String getText(@NotNull Properties attributes) throws IOException{
    return FileTemplateUtil.mergeTemplate(attributes, getText(), false);
  }

  @Override
  public final String @NotNull [] getUnsetAttributes(@NotNull Properties properties, @NotNull Project project) throws
                                                                                                               FileTemplateParseException {
    try {
    Set<String> attributes = new OrderedSet<>(Arrays.asList(FileTemplateUtil.calculateAttributes(getText(), properties, false, project)));
    attributes.addAll(Arrays.asList(FileTemplateUtil.calculateAttributes(getFileName(), properties, false, project)));
    return ArrayUtil.toStringArray(attributes);
    } catch (ParseException e) {
      throw new FileTemplateParseException(e);
    }
  }

  @Override
  public @NotNull FileTemplateBase clone() {
    try {
      return (FileTemplateBase)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isTemplateOfType(final @NotNull FileType fType) {
    return fType.equals(FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(getExtension()));
  }

  @Override
  public boolean isLiveTemplateEnabled() {
    return myLiveTemplateEnabled;
  }

  @Override
  public void setLiveTemplateEnabled(boolean value) {
    myLiveTemplateEnabledChanged |= myLiveTemplateEnabled != value;
    myLiveTemplateEnabled = value;
  }

  public boolean isLiveTemplateEnabledChanged() {
    return myLiveTemplateEnabledChanged;
  }

  public boolean isLiveTemplateEnabledByDefault() { return false; }

  @Override
  public @NotNull String getFileName() {
    return myFileName;
  }

  @Override
  public void setFileName(@NotNull String fileName) {
    myFileName = fileName;
  }

  @Override
  public FileTemplate @NotNull[] getChildren() {
    return myChildren;
  }

  @Override
  public void setChildren(FileTemplate @NotNull[] children) {
    myChildren = children;
  }

  public void addChild(FileTemplate child) {
    myChildren = ArrayUtil.append(getChildren(), child);
  }

  public String getChildName(int index) {
    return getQualifiedName() + TEMPLATE_CHILDREN_SUFFIX + index;
  }

  public void updateChildrenNames() {
    FileTemplate @NotNull [] children = getChildren();
    for (int i = 0; i < children.length; i++) {
      children[i].setName(getChildName(i));
    }
  }

  public static boolean isChild(@NotNull FileTemplate template) {
    return template.getName().contains(TEMPLATE_CHILDREN_SUFFIX);
  }
}