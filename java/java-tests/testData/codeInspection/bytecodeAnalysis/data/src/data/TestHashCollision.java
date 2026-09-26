package bytecodeAnalysis.data;

public final class TestHashCollision {
  // signature hashes for these two methods collide (see HMember implementation: murmur hash is used)
  void test4691() {
    // Though purity can be inferred for this method, due to collision we erase inference result
  }

  void test184537() {
    System.out.println("non-pure");
  }
}