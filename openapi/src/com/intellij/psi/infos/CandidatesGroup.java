/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
