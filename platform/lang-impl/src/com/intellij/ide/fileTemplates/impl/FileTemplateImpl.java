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

package com.intellij.ide.fileTemplates.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.ArrayUtil;
import org.apache.velocity.runtime.parser.ParseException;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Map;
import java.util.Properties;

/**
 * @author MYakovlev
 * Date: Jul 24, 2002
 */
public class FileTemplateImpl implements FileTemplate, Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FileTemplateImpl");

  private String myDescription;
  private String myContent;
  private String myName;
  private String myExtension;
  private File myTemplateFile;      // file to save in
  private String myTemplateURL;
  private boolean myRenamed = false;
  private boolean myModified = false;
  private boolean myReadOnly = false;
  private boolean myAdjust = true;

 private boolean myIsInternal = false;

  /** Creates new template. This template is marked as 'new', i.e. it will be saved to new file at IDEA end. */
  FileTemplateImpl(@NotNull String content, @NotNull String name, @NotNull String extension){
    myContent = StringUtil.convertLineSeparators(content);
    myName = replaceFileSeparatorChar(name);
    myExtension = extension;
    myModified = true;
  }

  FileTemplateImpl(@NotNull File templateFile, @NotNull String name, @NotNull String extension, boolean isReadOnly) {
    myTemplateFile = templateFile;
    myName = replaceFileSeparatorChar(name);
    myExtension = extension;
    myModified = false;
    myReadOnly = isReadOnly;
  }

  FileTemplateImpl(@NotNull VirtualFile templateURL, @NotNull String name, @NotNull String extension) {
    myTemplateURL = templateURL.getUrl();
    myName = name;
    myExtension = extension;
    myModified = false;
    myReadOnly = true;
  }

  public Object clone(){
    try{
      return super.clone();
    }
    catch (CloneNotSupportedException e){
      // Should not be here
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public String[] getUnsetAttributes(@NotNull Properties properties) throws ParseException{
    String content;
    try{
      content = getContent();
    }
    catch (IOException e){
      LOG.error("Unable to read template \""+myName+"\"", e);
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return FileTemplateUtil.calculateAttributes(content, properties, false);
  }

  public synchronized boolean isDefault(){
    return myReadOnly;
  }

  @NotNull
  public String getDescription(){
    try {
      String description;
      synchronized (this) {
        description = myDescription;
      }
      if (description == null) return "";
      VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(description);
      LOG.assertTrue(virtualFile != null, "Unable to find description at '" + description + "'");
      return VfsUtil.loadText(virtualFile);
    }
    catch (IOException e) {
      return "";
    }
  }

  synchronized void setDescription(VirtualFile file){
    myDescription = file.getUrl();
  }

  @NotNull
  public synchronized String getName(){
    return myName;
  }

  public synchronized boolean isJavaClassTemplate(){
    FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(myExtension);
    return fileType.equals(StdFileTypes.JAVA);
  }

  @NotNull
  public synchronized String getExtension(){
    return myExtension;
  }

  @NotNull
  public String getText(){
    try{
      return getContent();
    }
    catch (IOException e){
      LOG.error("Unable to read template \""+myName+"\"", e);
      return "";
    }
  }

  public synchronized void setText(String text){
    // for read-only template we will save it later in user-defined templates
    if(text == null){
      text = "";
    }
    text = StringUtil.convertLineSeparators(text);
    if(text.equals(getText())){
      return;
    }
    myContent = text;
    myModified = true;
    if(myReadOnly){
      myTemplateFile = null;
      myTemplateURL = null;
      myReadOnly = false;
    }
  }

  synchronized boolean isModified(){
    return myModified;
  }

  /** Read template from file. */
  private static String readExternal(File file) throws IOException{
    return FileUtil.loadFile(file, ourEncoding);
  }

  /** Read template from URL. */
  private static String readExternal(VirtualFile url) throws IOException{
    final Document content = FileDocumentManager.getInstance().getDocument(url);
    return content != null ? content.getText() : new String(url.contentsToByteArray(), ourEncoding);
  }

  /** Removes template file.
   */
  synchronized void removeFromDisk() {
    if (!myReadOnly && myTemplateFile != null && myTemplateFile.delete()) {
      myModified = false;
    }
  }

  /** Save template to file. If template is new, it is saved to specified directory. Otherwise it is saved to file from which it was read.
   *  If template was not modified, it is not saved.
   */
  void writeExternal(File defaultDir) throws IOException{
    File templateFile;
    synchronized (this) {
      if (!myModified && !myRenamed) {
        return;
      }
      if(myRenamed){
        LOG.assertTrue(myTemplateFile != null);
        LOG.assertTrue(myTemplateFile.delete());
        myTemplateFile = null;
        myRenamed = false;
      }
      templateFile = myReadOnly ? null : myTemplateFile;
      if(templateFile == null){
        LOG.assertTrue(defaultDir.isDirectory());
        templateFile = new File(defaultDir, myName+"."+myExtension);
      }
    }

    FileOutputStream fileOutputStream = new FileOutputStream(templateFile);
    OutputStreamWriter outputStreamWriter;
    try{
      outputStreamWriter = new OutputStreamWriter(fileOutputStream, ourEncoding);
    }
    catch (UnsupportedEncodingException e){
      Messages.showMessageDialog(IdeBundle.message("error.unable.to.save.file.template.using.encoding", getName(), ourEncoding),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      outputStreamWriter = new OutputStreamWriter(fileOutputStream);
    }
    String content = getContent();
    Project project = ProjectManagerEx.getInstanceEx().getDefaultProject();
    String lineSeparator = CodeStyleSettingsManager.getSettings(project).getLineSeparator();

    if (!lineSeparator.equals("\n")){
      content = StringUtil.convertLineSeparators(content, lineSeparator);
    }

    outputStreamWriter.write(content);
    outputStreamWriter.close();
    fileOutputStream.close();

//    StringReader reader = new StringReader(getContent());
//    FileWriter fileWriter = new FileWriter(templateFile);
//    BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
//    for(int currChar = reader.read(); currChar != -1; currChar = reader.read()){
//      bufferedWriter.write(currChar);
//    }
//    bufferedWriter.close();
//    fileWriter.close();
    synchronized (this) {
      myModified = false;
      myTemplateFile = templateFile;
    }
  }

  @NotNull
  public String getText(Map attributes) throws IOException{
    return StringUtil.convertLineSeparators(FileTemplateUtil.mergeTemplate(attributes, getContent()));
  }

  @NotNull
  public String getText(Properties attributes) throws IOException{
    return StringUtil.convertLineSeparators(FileTemplateUtil.mergeTemplate(attributes, getContent()));
  }

  public String toString(){
    return getName();
  }

  @NotNull
  private String getContent() throws IOException{
    String content;
    File templateIOFile;
    String templateURL;
    synchronized (this) {
      content = myContent;
      templateIOFile = myTemplateFile;
      templateURL = myTemplateURL;
    }
    if(content == null) {
      if(templateIOFile != null){
        content = StringUtil.convertLineSeparators(readExternal(templateIOFile));
      }
      else {
        if(templateURL != null){
          VirtualFile templateFile = VirtualFileManager.getInstance().findFileByUrl(templateURL);
          content = templateFile == null ? "" : StringUtil.convertLineSeparators(readExternal(templateFile));
        }
        else{
          content = "";
        }
      }
      synchronized (this) {
        myContent = content;
      }
    }

    return content;
  }

  synchronized void invalidate(){
    if(!myReadOnly){
      if(myTemplateFile != null || myTemplateURL != null){
        myContent = null;
      }
    }
  }

  synchronized boolean isNew(){
    return myTemplateFile == null && myTemplateURL == null;
  }

  public synchronized void setName(@NotNull String name){
    name = replaceFileSeparatorChar(name.trim());
    if(!myName.equals(name)){
      LOG.assertTrue(!myReadOnly);
      myName = name;
      myRenamed = true;
      myModified = true;
    }
  }

  public synchronized void setExtension(@NotNull String extension){
    extension = extension.trim();
    if(!myExtension.equals(extension)){
      LOG.assertTrue(!myReadOnly);
      myExtension = extension;
      myRenamed = true;
      myModified = true;
    }
  }

  public synchronized boolean isAdjust(){
    return myAdjust;
  }

  public synchronized void setAdjust(boolean adjust){
    myAdjust = adjust;
  }

  public void resetToDefault() {
    LOG.assertTrue(!isDefault());
    String name;
    String extension;
    synchronized (this) {
      name = myName;
      extension = myExtension;
    }
    VirtualFile file = FileTemplateManagerImpl.getInstanceImpl().getDefaultTemplate(name, extension);
    if (file == null) return;
    try {
      String text = readExternal(file);
      setText(text);
      synchronized (this) {
        myReadOnly = true;
      }
    }
    catch (IOException e) {
      LOG.error ("Error reading template");
    }
  }

  private static String replaceFileSeparatorChar(String s) {
    StringBuilder buffer = new StringBuilder();
    char[] chars = s.toCharArray();
    for (char aChar : chars) {
      if (aChar == File.separatorChar) {
        buffer.append("$");
      }
      else {
        buffer.append(aChar);
      }
    }
    return buffer.toString();
  }

  public synchronized void setInternal(boolean isInternal) {
    myIsInternal = isInternal;
  }

  public synchronized boolean isInternal() {
    return myIsInternal;
  }

  synchronized void setModified(boolean modified) {
    myModified = modified;
  }

  synchronized void setReadOnly(boolean readOnly) {
    myReadOnly = readOnly;
  }
}
