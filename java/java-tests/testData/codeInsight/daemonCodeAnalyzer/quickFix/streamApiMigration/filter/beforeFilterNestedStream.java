// "Replace with findFirst()" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  class MappedField{
    List<MappedField> getLoadNames(){return null;}
  }

  public MappedField getMappedField(final String storedName) {
    List<MappedField> persistenceFields = new ArrayList<>();
    for<caret> (final MappedField mf : persistenceFields) {
      for (final String n : mf.getLoadNames()) {
        if (storedName.equals(n)) {
          return mf;
        }
      }
    }
    return null;
  }
}