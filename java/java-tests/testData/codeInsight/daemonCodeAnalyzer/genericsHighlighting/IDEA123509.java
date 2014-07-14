class Test {
  interface IDo {}
  interface IDoable<TDo> {}
  interface IDoableFilter<TDo, TDoable extends IDoable<TDo>> {}

  <TDoable extends IDoable<IDo>> void foo(IDoableFilter<IDo, ? super TDoable> filter_) {
    IDoableFilter<IDo, ? super TDoable> filter = filter_;
  }
}
