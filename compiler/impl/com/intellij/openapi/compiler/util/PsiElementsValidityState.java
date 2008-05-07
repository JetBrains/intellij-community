package com.intellij.openapi.compiler.util;

import com.intellij.compiler.CompilerIOUtil;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.io.IOUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PsiElementsValidityState implements ValidityState {
  private final Map<String, Long> myDependencies = new HashMap<String, Long>();

  public PsiElementsValidityState() {
  }

  public void addDependency(final String key, final Long value) {
    myDependencies.put(key, value);
  }

  public boolean equalsTo(ValidityState otherState) {
    return otherState instanceof PsiElementsValidityState &&
           myDependencies.equals(((PsiElementsValidityState)otherState).myDependencies);
  }

  public void save(DataOutput out) throws IOException {
    final Set<Map.Entry<String, Long>> entries = myDependencies.entrySet();
    out.writeInt(entries.size());
    for (Map.Entry<String, Long> entry : entries) {
      IOUtil.writeString(entry.getKey(), out);
      out.writeLong(entry.getValue().longValue());
    }
  }

  public static PsiElementsValidityState load(DataInput input) throws IOException {
    int size = input.readInt();
    final PsiElementsValidityState state = new PsiElementsValidityState();
    while (size-- > 0) {
      final String s = CompilerIOUtil.readString(input);
      final long timestamp = input.readLong();
      state.addDependency(s, timestamp);
    }
    return state;
  }

  public void addDependency(final PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile != null) {
      VirtualFile file = psiFile.getVirtualFile();
      if (file != null) {
        addDependency(file.getUrl(), file.getTimeStamp());
      }
    }
  }
}