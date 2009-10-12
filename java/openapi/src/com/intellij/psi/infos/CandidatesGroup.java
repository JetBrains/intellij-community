/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.infos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.06.2003
 * Time: 13:37:39
 * To change this template use Options | File Templates.
 */
public class CandidatesGroup {
  public static final int UNKNOWN = -1;
  public static final int CONFLICT = 0;

  private List myCandidates = new ArrayList();
  private int myCause = UNKNOWN;

  public CandidateInfo get(int index){
    return (CandidateInfo) myCandidates.get(index);
  }

  public int size(){
    return myCandidates.size();
  }

  public Iterator iterator(){
    return myCandidates.iterator();
  }

  public int getCause(){
    return myCause;
  }

  public CandidatesGroup(List candidates, int cause){
    myCandidates = candidates;
    myCause = cause;
  }

  public CandidatesGroup(int cause){
    myCause = cause;
  }

  public CandidatesGroup(){}

  public void add(CandidateInfo candidate){
    myCandidates.add(candidate);
  }
}
