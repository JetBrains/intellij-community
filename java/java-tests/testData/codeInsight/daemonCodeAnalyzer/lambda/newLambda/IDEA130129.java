import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

class Test
{
  public static class WrapperBase<T, E>
  {
    public T unwrap(E edge)
    {
      //noinspection unchecked
      return (T)((DefaultEdge)edge).getSource();
    }
  }

  public static class Wrapper<T, E> extends WrapperBase<T, E>
  {
  }

  public static class DefaultEdge
  {
    private final TaskId taskId;

    public DefaultEdge(TaskId taskId)
    {
      this.taskId = taskId;
    }

    public Object getSource()
    {
      return taskId;
    }
  }

  public static class TaskId
  {
    private final String id;

    public TaskId(String id)
    {
      this.id = id;
    }

    public String getId()
    {
      return id;
    }

    @Override
    public String toString()
    {
      return getId();
    }
  }

  public static void main(String[] args)
  {
    Wrapper<TaskId, DefaultEdge> w = new Wrapper<>();
    Set<DefaultEdge> s = new HashSet<>();
    s.add(new DefaultEdge(new TaskId("1")));
    s.add(new DefaultEdge(new TaskId("2")));
    s.add(new DefaultEdge(new TaskId("3")));

    Set<TaskId> processed = s
      .stream()
      .map(w::unwrap)
      .filter(edge -> !edge.getId().equals(""))
      .collect(Collectors.toSet());

    System.out.println(processed);
  }
}
