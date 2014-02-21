import java.util.*;
import java.util.stream.*;

class Test {

  {
    Collector<Employee, ?, Map<Department, List<Employee>>> objectMapCollector = Collectors.groupingBy(Employee::getDepartment);
  }

  private class Employee {
    public Department getDepartment() {
      return new Department();
    }
  }

  private class Department {}
}
