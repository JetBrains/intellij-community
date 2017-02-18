/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.todo.HighlightedRegionProvider;
import com.intellij.ide.todo.SmartTodoItemPointer;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.TodoItem;
import com.intellij.ui.HighlightedRegion;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class TodoItemNode extends BaseToDoNode<SmartTodoItemPointer> implements HighlightedRegionProvider{
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.toDo.TodoItemNodeDescriptor");

  private final ArrayList<HighlightedRegion> myHighlightedRegions;

  public TodoItemNode(Project project,
                      SmartTodoItemPointer value,
                      TodoTreeBuilder builder) {
    super(project, value, builder);
    RangeMarker rangeMarker = getValue().getRangeMarker();
    LOG.assertTrue(rangeMarker.isValid());

    myHighlightedRegions= new ArrayList<>();

  }

  @Override
  public int getFileCount(final SmartTodoItemPointer val) {
    return 1;
  }

  @Override
  public int getTodoItemCount(final SmartTodoItemPointer val) {
    return 1;
  }

  @Override
  public ArrayList<HighlightedRegion> getHighlightedRegions(){
    return myHighlightedRegions;
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public void update(PresentationData presentation) {
    TodoItem todoItem=getValue().getTodoItem();
    RangeMarker myRangeMarker=getValue().getRangeMarker();
    if (!todoItem.getFile().isValid() || !myRangeMarker.isValid() || myRangeMarker.getStartOffset() == myRangeMarker.getEndOffset()) {
      myRangeMarker.dispose();
      setValue(null);
      return;
    }

    myHighlightedRegions.clear();

    // Update name

    Document document=getValue().getDocument();
    CharSequence chars = document.getCharsSequence();
    int startOffset=myRangeMarker.getStartOffset();
    int endOffset=myRangeMarker.getEndOffset();

    LOG.assertTrue(startOffset>-1);
    LOG.assertTrue(startOffset<=document.getTextLength());
    LOG.assertTrue(endOffset>-1);
    LOG.assertTrue(endOffset<document.getTextLength()+1);

    int lineNumber = document.getLineNumber(startOffset);
    LOG.assertTrue(lineNumber>-1);
    LOG.assertTrue(lineNumber<document.getLineCount());

    int lineStartOffset = document.getLineStartOffset(lineNumber);
    LOG.assertTrue(lineStartOffset>-1);
    LOG.assertTrue(lineStartOffset<=startOffset);
    LOG.assertTrue(lineStartOffset<=document.getTextLength());

    int columnNumber=startOffset-lineStartOffset;
    LOG.assertTrue(columnNumber>-1);

    // skip all white space characters

    while(lineStartOffset < document.getTextLength() && (chars.charAt(lineStartOffset) == '\t' || chars.charAt(lineStartOffset)==' ')) {
      lineStartOffset++;
    }

    int lineEndOffset = document.getLineEndOffset(lineNumber);
    LOG.assertTrue(lineEndOffset>=0);
    LOG.assertTrue(lineEndOffset<=document.getTextLength());

    String lineColumnPrefix="("+(lineNumber+1)+", "+(columnNumber+1)+") ";

    String highlightedText = chars.subSequence(lineStartOffset, Math.min(lineEndOffset, chars.length())).toString();

    String newName=lineColumnPrefix+highlightedText;

    // Update icon

    Icon newIcon=todoItem.getPattern().getAttributes().getIcon();

    // Update highlighted regions

    myHighlightedRegions.clear();
    EditorHighlighter highlighter = myBuilder.getHighlighter(todoItem.getFile(),document);
    HighlighterIterator iterator=highlighter.createIterator(lineStartOffset);
    while(!iterator.atEnd()){
      int start=Math.max(iterator.getStart(),lineStartOffset);
      int end=Math.min(iterator.getEnd(),lineEndOffset);
      if(lineEndOffset<start||lineEndOffset<end){
        break;
      }
      TextAttributes attributes=iterator.getTextAttributes();
      int fontType = attributes.getFontType();
      if ((fontType & Font.BOLD) != 0){ // suppress bold attribute
        attributes = attributes.clone();
        attributes.setFontType(fontType & ~Font.BOLD);
      }
      HighlightedRegion region=new HighlightedRegion(
        lineColumnPrefix.length()+start-lineStartOffset,
        lineColumnPrefix.length()+end-lineStartOffset,
        attributes
      );
      myHighlightedRegions.add(region);
      iterator.advance();
    }

    TextAttributes attributes=todoItem.getPattern().getAttributes().getTextAttributes();
    HighlightedRegion region=new HighlightedRegion(
      lineColumnPrefix.length()+startOffset-lineStartOffset,
      lineColumnPrefix.length()+endOffset-lineStartOffset,
      attributes
    );
    myHighlightedRegions.add(region);

    //

    presentation.setPresentableText(newName);
    presentation.setIcon(newIcon);
  }

  @Override
  public String getTestPresentation() {
    return "Item: "+getValue().getTodoItem().getTextRange();
  }

  @Override
  public int getWeight() {
    return 5;
  }
}
