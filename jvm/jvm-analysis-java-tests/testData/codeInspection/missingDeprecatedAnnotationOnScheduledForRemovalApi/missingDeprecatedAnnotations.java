package test;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.ScheduledForRemoval
class <error descr="Scheduled for removal API must also be marked with '@Deprecated' annotation">Warnings</error> {

  @ApiStatus.ScheduledForRemoval
  public String <error descr="Scheduled for removal API must also be marked with '@Deprecated' annotation">field</error>;

  @ApiStatus.ScheduledForRemoval
  public void <error descr="Scheduled for removal API must also be marked with '@Deprecated' annotation">method</error>() {
  }
}

//No warnings should be produced.

@Deprecated
@ApiStatus.ScheduledForRemoval
class NoWarnings {

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public String field;

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public void method() {
  }
}