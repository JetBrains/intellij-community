/**
 * created at Jan 4, 2002
 * @author Jeka
 */
package com.intellij.compiler.make;

import com.intellij.compiler.classParsing.FieldInfo;
import com.intellij.compiler.classParsing.MemberInfo;
import com.intellij.compiler.classParsing.MethodInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Dependency {
  public static final Dependency[] EMPTY_ARRAY = new Dependency[0];
  private final int myClassQualifiedName;
  private Set<MemberInfo> myUsedMembers; // members which are used
  private MemberInfo[] myMemberInfoArray = MemberInfo.EMPTY_MEMBER_INFO_ARRAY; // cached data
  private MethodInfo[] myMethodInfoArray = MethodInfo.EMPTY_ARRAY; // cached data
  private FieldInfo[] myFieldInfoArray = FieldInfo.EMPTY_ARRAY; // cached data

  public Dependency(int classQualifiedName) {
    myClassQualifiedName = classQualifiedName;
  }

  public int getClassQualifiedName() {
    return myClassQualifiedName;
  }

  public void addMemberInfo(MemberInfo info) {
    if (myUsedMembers == null) {
      myUsedMembers = new HashSet<MemberInfo>();
    }
    myUsedMembers.add(info);
    myMemberInfoArray = null;
    myMethodInfoArray = null;
    myFieldInfoArray = null;
  }

  @NotNull
  public MemberInfo[] getUsedMembers() {
    if (myMemberInfoArray == null) {
      if (myUsedMembers != null && !myUsedMembers.isEmpty()) {
        myMemberInfoArray = myUsedMembers.toArray(new MemberInfo[myUsedMembers.size()]);
      }
      else {
        myMemberInfoArray = MemberInfo.EMPTY_MEMBER_INFO_ARRAY;
      }
    }
    return myMemberInfoArray;
  }

  public MethodInfo[] getUsedMethods() {
    if (myMethodInfoArray == null) {
      MemberInfo[] usedMembers = getUsedMembers();
      ArrayList<MemberInfo> list = null;
      for (MemberInfo memberInfo : usedMembers) {
        if (memberInfo instanceof MethodInfo) {
          if (list == null) {
            list = new ArrayList<MemberInfo>(usedMembers.length);
          }
          list.add(memberInfo);
        }
      }
      myMethodInfoArray = list != null ? list.toArray(new MethodInfo[list.size()]) : MethodInfo.EMPTY_ARRAY;
    }
    return myMethodInfoArray;
  }

  public FieldInfo[] getUsedFields() {
    if (myFieldInfoArray == null) {
      ArrayList<MemberInfo> list = null;
      MemberInfo[] usedMembers = getUsedMembers();
      for (MemberInfo memberInfo : usedMembers) {
        if (memberInfo instanceof FieldInfo) {
          if (list == null) {
            list = new ArrayList<MemberInfo>(usedMembers.length);
          }
          list.add(memberInfo);
        }
      }
      myFieldInfoArray = list != null ? list.toArray(new FieldInfo[list.size()]) : FieldInfo.EMPTY_ARRAY;
    }
    return myFieldInfoArray;
  }

}
