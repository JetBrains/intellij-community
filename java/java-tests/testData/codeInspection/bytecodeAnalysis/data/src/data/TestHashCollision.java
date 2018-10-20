package bytecodeAnalysis.data;

public final class TestHashCollision {
  // signature hashes for these two methods collide: MD5("()V"+"test11044") and MD5("()V"+"test20917") have the same prefix: 3d802c48
  void test11044() {
    // Though purity can be inferred for this method, due to collision we erase inference result
  }

  void test20917() {
    System.out.println("non-pure");
  }
}