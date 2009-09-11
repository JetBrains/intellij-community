package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.08.2003
 * Time: 16:20:00
 * To change this template use Options | File Templates.
 */
public class TestLookupManager extends LookupManagerImpl{
  public TestLookupManager(Project project, MessageBus bus){
    super(project, bus);
  }

  public void forceSelection(char completion, int index){
    if(myActiveLookup == null) throw new RuntimeException("There are no items in this lookup");
    final LookupElement lookupItem = myActiveLookup.getItems().get(index);
    myActiveLookup.setCurrentItem(lookupItem);
    myActiveLookup.finishLookup(completion);
  }

  public void forceSelection(char completion, LookupElement item){
    myActiveLookup.setCurrentItem(item);
    myActiveLookup.finishLookup(completion);
  }

  public void clearLookup() {
    if (myActiveLookup != null) {
      myActiveLookup.hide();
      myActiveLookup = null;
    }
  }

}
