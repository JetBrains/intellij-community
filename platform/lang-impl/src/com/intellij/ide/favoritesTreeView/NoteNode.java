/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.favoritesTreeView;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 5/31/12
 * Time: 8:47 PM
 */
public class NoteNode {
  private PercentDone myPercentDone = PercentDone._0;
  private Set<Flag> myFlags;
  private Set<Concept> myConcepts;

  private String myText;
  private final boolean myReadonly;

  public NoteNode(String text, boolean readonly) {
    myText = text;
    myReadonly = readonly;
  }

  public PercentDone getPercentDone() {
    return myPercentDone;
  }

  public void setPercentDone(PercentDone percentDone) {
    myPercentDone = percentDone;
  }

  public Set<Flag> getFlags() {
    return myFlags;
  }

  public void setFlags(Set<Flag> flags) {
    myFlags = flags;
  }

  public Set<Concept> getConcepts() {
    return myConcepts;
  }

  public void setConcepts(Set<Concept> concepts) {
    myConcepts = concepts;
  }

  public String getText() {
    return myText;
  }

  public void setText(String text) {
    myText = text;
  }

  public boolean isReadonly() {
    return myReadonly;
  }
}
