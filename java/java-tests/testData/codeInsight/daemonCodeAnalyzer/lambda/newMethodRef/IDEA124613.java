import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class Scratch
{
  public static void main(String[] args)
  {
    final List<ConcreteId> list = new ArrayList<>();

    final List<Long> longs =  list.stream()
      .map(ConcreteId::getId)
      .collect(Collectors.toList());
    
    final List<Long> longs2 =  list.stream()
      .map(AbstractId::getId)
      .collect(Collectors.toList());
  }

  private static abstract class AbstractId
  {
    private final long id;

    public AbstractId(long id)
    {
      this.id = id;
    }

    public long getId()
    {
      return this.id;
    }
  }

  private static class ConcreteId extends AbstractId
  {
    public ConcreteId(long id)
    {
      super(id);
    }
  }
}
