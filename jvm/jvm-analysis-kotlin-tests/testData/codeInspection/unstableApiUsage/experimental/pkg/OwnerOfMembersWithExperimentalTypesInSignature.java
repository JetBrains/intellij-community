package pkg;

import annotatedPkg.ClassInAnnotatedPkg;

public class OwnerOfMembersWithExperimentalTypesInSignature {
  public AnnotatedClass field;

  public ClassInAnnotatedPkg fieldPkg;

  public void parameterType(AnnotatedClass param) { }

  public void parameterTypePkg(ClassInAnnotatedPkg param) { }

  public AnnotatedClass returnType() { return null; }

  public AnnotatedClass returnTypePkg() { return null; }
}