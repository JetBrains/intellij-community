import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class Test {
  private static void test(@NotNull IfStmt ifStatement) {
    IfStmt currentIf = ifStatement;
    boolean flag = false;
    while (true) {
      if (currentIf.getElseBranch() != null) return;
      if(currentIf.getCondition() != null && checkCondition(currentIf.getCondition())) {
        flag = true;
      }
      Stmt sibling = currentIf;
      do {
        sibling = getNext(sibling);
      }
      while (sibling != null);

      IfStmt enclosingIf = getEnclosing(currentIf);
      if (enclosingIf == null) break;
      currentIf = enclosingIf;
    }
    if(flag) {
      System.out.println("not always");
    }
  }

  interface Stmt {}

  interface IfStmt extends Stmt {
    @Nullable Object getElseBranch();
    @Nullable Object getCondition();
  }

  static native @Nullable Stmt getNext(Stmt cur);
  static native IfStmt getEnclosing(Stmt cur);
  static native boolean checkCondition(Object condition);

}