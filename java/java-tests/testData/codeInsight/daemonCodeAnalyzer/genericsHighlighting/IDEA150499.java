interface Root {
  Worker<? extends Root> foo();
}

interface Worker<T extends Root> {
}

interface SubRootA extends Root {
  @Override
  Worker<? extends SubRootA> foo();
}

interface SubRootB extends Root {
  @Override
  Worker<? extends SubRootB> foo();
}

interface Joined extends Root, SubRootA, SubRootB {
  @Override
  Worker<? extends Joined> foo();
}

interface Erroneously extends Joined {}