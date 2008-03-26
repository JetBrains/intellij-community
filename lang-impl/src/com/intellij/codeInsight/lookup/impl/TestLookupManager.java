package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.08.2003
 * Time: 16:20:00
 * To change this template use Options | File Templates.
 */
public class TestLookupManager extends LookupManagerImpl{
  private Project myProject;
  public TestLookupManager(Project project, MessageBus bus){
    super(project, bus);
    myProject = project;
  }

  public Lookup showLookup(final Editor editor, LookupItem[] items, String prefix, LookupItemPreferencePolicy itemPreferencePolicy, @Nullable String bottomText) {
    hideActiveLookup();

    myActiveLookup = new LookupImpl(myProject, editor, items, prefix, itemPreferencePolicy, bottomText);
    myActiveLookupEditor = editor;
    myActiveLookup.show();
    return myActiveLookup;
  }

  public void forceSelection(char completion, int index){
    if(myActiveLookup == null) throw new RuntimeException("There are no items in this lookup");
    final LookupItem[] items = myActiveLookup.getItems();
    final LookupItem lookupItem = items[index];
    myActiveLookup.setCurrentItem(lookupItem);
    myActiveLookup.finishLookup(completion);
  }

  public void forceSelection(char completion, LookupItem item){
    myActiveLookup.setCurrentItem(item);
    myActiveLookup.finishLookup(completion);
  }


  public LookupItem[] getItems(){
    return myActiveLookup != null ? myActiveLookup.getItems() : null;
  }
}
