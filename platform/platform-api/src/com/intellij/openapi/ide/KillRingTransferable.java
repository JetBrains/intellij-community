/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.ide;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * This class represents usual {@link StringSelection transferable string} with additional meta-information that describes the place
 * of the document that from there it was retrieved.
 * <p/>
 * The main idea is that we want to be able to combine adjacent text into single unit like
 * <a href="http://www.gnu.org/software/emacs/manual/html_node/emacs/Kill-Ring.html#Kill-Ring">emacs kill ring</a> does.
 * E.g. if the user invokes 'cut to the line end' subsequently we may want to paste all of them, hence, we need to be able
 * to distinguish if particular copy-paste strings are adjacent.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 4/15/11 6:29 PM
 */
public class KillRingTransferable implements Transferable {

  private static final DataFlavor[] DATA_FLAVORS = {DataFlavor.stringFlavor};

  private final String                  myData;
  private final WeakReference<Document> myDocument;
  private final int                     myStartOffset;
  private final int myEndOffset;
  private final boolean                 myCut;
  
  private volatile boolean myReadyToCombine = true;

  /**
   * Creates new <code>KillRingTransferable</code> object.
   * 
   * @param data                target text to transfer
   * @param document            document that contained given text
   * @param startOffset         start offset of the given text at the given document
   * @param endOffset       end offset of the given text during current object construction
   * @param cut                 flag that identifies whether target text was cut or copied from the document
   */
  public KillRingTransferable(@NotNull String data,
                              @NotNull Document document,
                              int startOffset,
                              int endOffset,
                              boolean cut)
  {
    myData = data;
    myDocument = new WeakReference<>(document);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myCut = cut;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return DATA_FLAVORS;
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor == DataFlavor.stringFlavor;
  }

  @Nullable
  @Override
  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    return isDataFlavorSupported(flavor) ? myData : null;
  }

  @Nullable
  public Document getDocument() {
    return myDocument.get();
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  /**
   * @return    offset of the target text end on the moment of the current object construction
   */
  public int getEndOffset() {
    return myEndOffset;
  }

  public boolean isCut() {
    return myCut;
  }

  /**
   * @return    <code>true</code> if current object can be combined with adjacent text; <code>false</code> otherwise.
   *            Default value is <code>true</code>
   */
  public boolean isReadyToCombine() {
    return myReadyToCombine;
  }

  /**
   * Allows to define if current object can be combined with adjacent text. 
   * 
   * @param readyToCombine    <code>true</code> if current object can be combined with adjacent text; <code>false</code> otherwise
   */
  public void setReadyToCombine(boolean readyToCombine) {
    myReadyToCombine = readyToCombine;
  }

  @Override
  public String toString() {
    return "data='" + myData + "', startOffset=" + myStartOffset + ", endOffset=" + myEndOffset +", cut=" + myCut;
  }
}
