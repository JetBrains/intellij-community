abstract class BaseTask<T, S extends BaseTask<T, S>> {
  public abstract S make<caret>Task(int depth, ParallelStream<T> coll);
}


class ForEachTask<T> extends BaseTask<T, ForEachTask<T>> {
  public ForEachTask<T> makeTask(int depth, ParallelStream<T> coll) {
    return new ForEachTask<T>();
  }
}

class ParallelStream<T> {
}
