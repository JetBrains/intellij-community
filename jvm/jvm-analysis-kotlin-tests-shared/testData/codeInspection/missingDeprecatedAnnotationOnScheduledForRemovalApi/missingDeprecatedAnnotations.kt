package test;

import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
class <error descr="Scheduled for removal API must also be marked with '@Deprecated' annotation">Warnings</error> {

  @ApiStatus.ScheduledForRemoval
  val <error descr="Scheduled for removal API must also be marked with '@Deprecated' annotation">field</error>: Int = 0

  @ApiStatus.ScheduledForRemoval
  fun <error descr="Scheduled for removal API must also be marked with '@Deprecated' annotation">method</error>() {
  }
}

//No warnings.

@ApiStatus.ScheduledForRemoval
@Deprecated("reason")
class NoWarnings {

  @ApiStatus.ScheduledForRemoval
  @Deprecated("reason")
  val field: Int = 0;

  @ApiStatus.ScheduledForRemoval
  @Deprecated("reason")
  public fun method() {
  }
}