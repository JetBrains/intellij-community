public class AssignToFinalInSwitchExpression {
  void test() {
    int x = switch (1) {
      default:
        final int var;
        <error descr="Variable 'var' might not have been initialized">var</error>++; // нет сообщения об ошибке
        yield 1;
    };
  }
}