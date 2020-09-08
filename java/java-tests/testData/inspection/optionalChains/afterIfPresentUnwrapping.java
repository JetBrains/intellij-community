// "Remove folded 'ifPresent()' call" "true"

import java.util.Optional;

public class After {
  static void setDefaultReviewerForTaskOldStyle(Task task) {
    task.getResponsible().flatMap(Person::getManager).ifPresent(task::setReviewer);
  }

  class Person {
    private final Department department; // can be null

    Person(Department department) {
      this.department = department;
    }

    private Optional<Person> getManager() {
      return Optional.ofNullable(department).map(Department::getManager);
    }
  }

  interface Department {
    Person getManager(); // never null
  }

  interface Task {
    Optional<Person> getResponsible();

    void setReviewer(Person person);
  }
}