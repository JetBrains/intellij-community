abstract class BaseTask<T, S extends BaseTask<T, S>> {
}


class ForEachTask<T> extends BaseTask<T, ForEachTask<T>> {
  public ForEachTask<T> makeTask(int depth, ParallelStream<T> coll) {
    return new ForEachTask<T>();
  }
}

class ParallelStream<T> {
}
