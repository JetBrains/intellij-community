/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.actions.GotoHomeAction;
import com.intellij.openapi.fileChooser.actions.GotoProjectDirectory;
import com.intellij.openapi.fileChooser.actions.NewFolderAction;
import com.intellij.openapi.fileChooser.actions.FileDeleteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ide.actions.SynchronizeAction;

public class FileSystemTreeFactoryImpl implements FileSystemTreeFactory{
  public FileSystemTree createFileSystemTree(Project project, FileChooserDescriptor fileChooserDescriptor) {
    return new FileSystemTreeImpl(project, fileChooserDescriptor);
  }

  public DefaultActionGroup createDefaultFileSystemActions(FileSystemTree fileSystemTree) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new GotoHomeAction(fileSystemTree));
    group.add(new GotoProjectDirectory(fileSystemTree));
    group.addSeparator();
    group.add(new NewFolderAction(fileSystemTree));
    group.add(new FileDeleteAction(fileSystemTree));
    group.addSeparator();
    SynchronizeAction action1 = new SynchronizeAction();
    AnAction original = ActionManager.getInstance().getAction(IdeActions.ACTION_SYNCHRONIZE);
    action1.copyFrom(original);
    action1.registerCustomShortcutSet(original.getShortcutSet(), fileSystemTree.getTree());
    group.add(action1);
    group.addSeparator();

    return group;
  }
}
