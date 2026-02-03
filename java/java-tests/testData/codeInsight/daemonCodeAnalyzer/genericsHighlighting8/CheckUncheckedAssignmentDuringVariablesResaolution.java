
import java.util.HashSet;

class Issue {
  public void some(String group) {
    new HashSet<Permission>().stream()
      .map(permission  -> (PrincipalPermission) permission)
      .filter(permission -> group.equals(permission.getGroup()));
  }
}

class Permission {
}

class PrincipalPermission<<warning descr="Type parameter 'T' is never used">T</warning>> extends Permission {
  public String getGroup() {
    return null;
  }
}