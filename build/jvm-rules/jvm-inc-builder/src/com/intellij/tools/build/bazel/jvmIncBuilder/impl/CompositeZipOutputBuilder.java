package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jetbrains.jps.util.Iterators.*;

public class CompositeZipOutputBuilder implements ZipOutputBuilder {
  private final List<ZipOutputBuilder> myDelegates = new ArrayList<>();

  public CompositeZipOutputBuilder(ZipOutputBuilder... delegates) {
    for (ZipOutputBuilder delegate : delegates) {
      if (delegate != null) {
        myDelegates.add(delegate);
      }
    }
  }
  
  public CompositeZipOutputBuilder(Iterable<ZipOutputBuilder> delegates) {
    collect(filter(delegates, Objects::nonNull), myDelegates);
  }

  @Override
  public Iterable<String> getEntryNames() {
    return flat(map(myDelegates, ZipOutputBuilder::getEntryNames));
  }

  @Override
  public Iterable<String> listEntries(String entryName) {
    return flat(map(myDelegates, b -> b.listEntries(entryName)));
  }

  @Override
  public byte @Nullable [] getContent(String entryName) {
    return find(map(myDelegates, b -> b.getContent(entryName)), Objects::nonNull);
  }

  @Override
  public void putEntry(String entryName, byte[] content) {
    for (ZipOutputBuilder delegate : myDelegates) {
      delegate.putEntry(entryName, content);
    }
  }

  @Override
  public boolean deleteEntry(String entryName) {
    boolean changes = false;
    for (ZipOutputBuilder delegate : myDelegates) {
      if (delegate.deleteEntry(entryName)) {
        changes = true;
      }
    }
    return changes;
  }

  @Override
  public void close() throws IOException {
    IOException ex = null;
    for (ZipOutputBuilder delegate : myDelegates) {
      try {
        delegate.close();
      }
      catch (IOException e) {
        if (ex == null) {
          ex = e;
        }
      }
    }
  }

  @Override
  public void close(boolean saveChanges) throws IOException {
    IOException ex = null;
    for (ZipOutputBuilder delegate : myDelegates) {
      try {
        delegate.close(saveChanges);
      }
      catch (IOException e) {
        if (ex == null) {
          ex = e;
        }
      }
    }
  }
}
