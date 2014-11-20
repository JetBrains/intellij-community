import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class IDEA129251 {
  void simplified(final Stream<String> pStream, final Stream<String> pStream1, final Stream<String> pStream2){
    pStream.flatMap(x -> Stream.concat(pStream1, pStream2.map(String::toUpperCase)));
  }


  private final Set<String> m_allSubtablesColumns;
  private final List<SubtableDescription> m_subtablesDescription = null;

  {
    m_allSubtablesColumns = m_subtablesDescription.stream().
      flatMap(desc -> Stream.concat(desc.getKeyColumns().stream().map(String::toUpperCase),
                                    desc.getValueColumns().stream().map(String::toUpperCase))).
      collect(Collectors.toSet());
  }

  abstract class SubtableDescription {
    abstract List<String> getKeyColumns();
    abstract List<String> getValueColumns();
  }
}