package com.intellij.history.core.changes;

import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.IdPath;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ContentChange extends StructuralChange<ContentChangeNonAppliedState, ContentChangeAppliedState> {
  public ContentChange(String path, Content newContent, long timestamp) {
    super(path);
    getNonAppliedState().myNewContent = newContent;
    getNonAppliedState().myNewTimestamp = timestamp;
  }

  public ContentChange(Stream s) throws IOException {
    super(s);
    getAppliedState().myOldContent = s.readContent();
    getAppliedState().myOldTimestamp = s.readLong();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeContent(getAppliedState().myOldContent);
    s.writeLong(getAppliedState().myOldTimestamp);
  }

  @Override
  protected ContentChangeAppliedState createAppliedState() {
    return new ContentChangeAppliedState();
  }

  @Override
  protected ContentChangeNonAppliedState createNonAppliedState() {
    return new ContentChangeNonAppliedState();
  }


  public Content getOldContent() {
    return getAppliedState().myOldContent;
  }

  public long getOldTimestamp() {
    return getAppliedState().myOldTimestamp;
  }

  @Override
  protected IdPath doApplyTo(Entry root, ContentChangeAppliedState newState) {
    Entry e = root.getEntry(getPath());

    newState.myOldContent = e.getContent();
    newState.myOldTimestamp = e.getTimestamp();

    e.changeContent(getNonAppliedState().myNewContent, getNonAppliedState().myNewTimestamp);
    return e.getIdPath();
  }

  @Override
  public void revertOn(Entry root) {
    Entry e = root.getEntry(getAffectedIdPath());
    e.changeContent(getAppliedState().myOldContent, getAppliedState().myOldTimestamp);
  }

  @Override
  public List<Content> getContentsToPurge() {
    return Collections.singletonList(getAppliedState().myOldContent);
  }

  @Override
  public boolean isFileContentChange() {
    return true;
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
