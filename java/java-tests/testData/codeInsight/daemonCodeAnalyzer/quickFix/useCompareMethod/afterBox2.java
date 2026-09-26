// "Fix all ''compare()' method can be used to compare numbers' problems in file" "true"
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class Dto{
  Long id;

  Long getId() {
    return id;
  }

  List<Long> sortIds(List<Dto> list) {
    List<Long> ids = list.stream().map(Dto::getId).collect(Collectors.toList());
    ids.sort(new Comparator<Long>() {
      public int compare(Long o1, Long o2) {
          return o1.compareTo(o2);
      }
    });
    return ids;
  }

  List<Dto> sortDtos(List<Dto> list) {
    list.sort(new Comparator<Dto>() {
      public int compare(Dto o1, Dto o2) {
          return o1.getId().compareTo(o2.getId());
      }
    });
    return list;
  }
}