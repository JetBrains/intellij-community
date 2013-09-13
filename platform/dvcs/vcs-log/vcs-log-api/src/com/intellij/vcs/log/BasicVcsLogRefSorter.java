package com.intellij.vcs.log;

import com.intellij.vcs.log.VcsLogRefSorter;
import com.intellij.vcs.log.VcsRef;

import java.util.*;

import static com.intellij.vcs.log.VcsRef.RefType.*;

/**
 * Compares references only by reference type and name.
 *
 * @author Kirill Likhodedov
 */
public class BasicVcsLogRefSorter implements VcsLogRefSorter {

  // first has the highest priority
  private static final List<VcsRef.RefType> REF_TYPE_PRIORITIES = Arrays.asList(HEAD, LOCAL_BRANCH, REMOTE_BRANCH, TAG);

  // -1 => higher priority
  public static final Comparator<VcsRef.RefType> REF_TYPE_COMPARATOR = new Comparator<VcsRef.RefType>() {
    @Override
    public int compare(VcsRef.RefType type1, VcsRef.RefType type2) {
      int p1 = REF_TYPE_PRIORITIES.indexOf(type1);
      int p2 = REF_TYPE_PRIORITIES.indexOf(type2);
      return p1 - p2;
    }
  };

  @Override
  public List<VcsRef> sort(Collection<VcsRef> refs) {
    ArrayList<VcsRef> list = new ArrayList<VcsRef>(refs);
    Collections.sort(list, new Comparator<VcsRef>() {
      @Override
      public int compare(VcsRef ref1, VcsRef ref2) {
        int typeComparison = REF_TYPE_COMPARATOR.compare(ref1.getType(), ref2.getType());
        if (typeComparison != 0) {
          return typeComparison;
        }
        return ref1.getName().compareTo(ref2.getName());
      }
    });
    return list;
  }

}
