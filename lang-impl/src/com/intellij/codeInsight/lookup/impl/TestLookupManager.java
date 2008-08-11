package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
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

  public Lookup showLookup(final Editor editor, LookupItem[] items, LookupItemPreferencePolicy itemPreferencePolicy, @Nullable String bottomText) {
    hideActiveLookup();

    for (final LookupItem item : items) {
      item.setPrefixMatcher(new CamelHumpMatcher(""));
    }
    myActiveLookup = new LookupImpl(myProject, editor, items, itemPreferencePolicy, bottomText);
    myActiveLookupEditor = editor;
    myActiveLookup.show();
    return myActiveLookup;
  }

  public void forceSelection(char completion, int index){
    if(myActiveLookup == null) throw new RuntimeException("There are no items in this lookup");
    final LookupElement[] items = myActiveLookup.getItems();
    final LookupElement lookupItem = items[index];
    myActiveLookup.setCurrentItem(lookupItem);
    myActiveLookup.finishLookup(completion);
  }

  public void forceSelection(char completion, LookupElement item){
    myActiveLookup.setCurrentItem(item);
    myActiveLookup.finishLookup(completion);
  }


  public LookupElement[] getItems(){
    return myActiveLookup != null ? myActiveLookup.getItems() : null;
  }
}
