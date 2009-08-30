/**
 * created at Jan 8, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MemberReferenceInfo extends ReferenceInfo {
  private final MemberInfo myMemberInfo;

  public MemberReferenceInfo(int declaringClass, MemberInfo memberInfo) {
    super(declaringClass);
    myMemberInfo = memberInfo;
  }

  public MemberReferenceInfo(DataInput in) throws IOException {
    super(in);
    myMemberInfo = MemberInfoExternalizer.loadMemberInfo(in);
  }

  public MemberInfo getMemberInfo() {
    return myMemberInfo;
  }

  public boolean isFieldReference() {
    return myMemberInfo instanceof FieldInfo;
  }

  public boolean isMethodReference() {
    return myMemberInfo instanceof MethodInfo;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    MemberInfoExternalizer.saveMemberInfo(out, myMemberInfo);
  }

  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    return myMemberInfo.equals(((MemberReferenceInfo)o).myMemberInfo);
  }

  public int hashCode() {
    return super.hashCode() + myMemberInfo.hashCode();
  }

  public @NonNls String toString() { // for debug purposes
    return "Member reference: [class name=" + getClassName() + ", member name = " + myMemberInfo.getName() + ", member descriptor=" + myMemberInfo.getDescriptor() + ", member signature=" + myMemberInfo.getGenericSignature() + "]";
  }
}
