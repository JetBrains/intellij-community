import org.jetbrains.annotations.*;

// IDEA-172844
abstract class SimpleClass {

  @Nullable
  protected MyType findSelfOrInnerByNonQualifiedName() {
    MyType nearestOuter = getOwnerParentOfType(MyType.class);
    if (nearestOuter != null) {
      MyType currentOuter = nearestOuter;
      while (currentOuter.getOuterClass() != null) { // False positive error here
        currentOuter = currentOuter.getOuterClass();
      }
    }

    return null;
  }

  @Nullable
  abstract MyType getOwnerParentOfType(Class<MyType> apexPsiTypeDeclarationClass);

  static abstract class MyType {

    @Nullable
    abstract MyType getOuterClass();
  }
}