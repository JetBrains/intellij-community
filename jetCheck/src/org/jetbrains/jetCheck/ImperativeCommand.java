/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Represents an action with potential side effects, for single-threaded property-based testing of stateful systems.
 * The engine executes a top-level command, which should prepare the system-under-test and run the sequence of nested (actual) commands
 * that change the system, check the needed invariants, and log all activity to allow to reproduce failing scenarios.<p/>
 * 
 * A typical way to test with imperative commands looks like this:
 * <pre>
 *   ImperativeCommand.checkScenarios(() -> env -> {
 *     System sys = setUpSystem();
 *     try {
 *       // run a random sequence of commands
 *       env.performCommands(Generator.sampledFrom(new Command1(sys), new Command2(sys), ...))
 *       assertPropertyHolds(sys); // optional; fail with an exception if some invariant is broken 
 *     } finally {
 *       tearDownSystem(); // if any resources should be freed
 *     }
 *   })
 *   ...
 *   class Command1 implements ImperativeCommand {
 *     System sys;
 *     Command1(System sys) {
 *       this.sys = sys;
 *     }
 *     
 *     public void performCommand(Environment env) {
 *       env.logMessage("Perforing command1");
 *       // do some actions on 'sys' using environment to generate random data (and log it) on the way, e.g.:
 *       Item item = env.generateData(Generator.sampledFrom(sys.currentItems), "working on %s item");
 *       modifyItemInTheSystem(sys, item); // may change the system and the items within it
 *     }
 *   }
 *   ...
 * </pre>
 * 
 * For more fine-grained control, you can use {@link #scenarios} to get a usual {@code PropertyChecker}. Then it's possible to
 * customize, e.g. iteration count:
 *   <pre>PropertyChecker.forAll(ImperativeCommand.scenarios(() -> env -> ...))
 *   .withIterationCount(42)
 *   .shouldHold(Scenario::ensureSuccessful)}</pre>
 * 
 * If any command fails with an exception, the property being checked is considered to be falsified and the test fails.
 * In the error message, the causing exception is printed together with command log.
 * Commands can and should log what they're doing using
 * {@link Environment#logMessage(String)} and {@link Environment#generateValue(Generator, String)} so that you
 * can restore the order of events that leads to the failure.<p/>
 * 
 * Top-level command should not have any side effects on the outside world, 
 * otherwise proper test case separation and minimization will be impossible.
 * The supplier that creates the top-level command should 
 * return a "fresh" object each time, as it's invoked on each testing and minimization iterations. 
 * Nested commands may have side effects, provided that those effects are fully contained within the system-under-test, 
 * which is set up and disposed inside the top-level command on each iteration.<p/>
 * 
 * Test case minimization is complicated, when commands make random choices based on the current state of the system
 * (which can change even after removing irrelevant commands during minimization).
 * The engine tries to account for that heuristically. <b>Rule of thumb</b>: whenever your command generates an index into some list,
 * or uses {@link Generator#sampledFrom} on system elements, try to ensure that these elements have some predictable ordering.
 * It's ideal if each command effect on these elements is either adding or removing a contiguous range in that list. 
 */
public interface ImperativeCommand {
  /** Perform the actual change on the system-under-test, using {@code env} to generate random data and log the progress */ 
  void performCommand(@NotNull Environment env);

  /** A helper object passed into {@link #performCommand} to allow for logging and ad hoc random data generation */
  interface Environment {
    
    /** Add a log message. The whole execution log would be printed if the command fails */
    void logMessage(@NotNull String message);
    
    /** Generate a pseudo-random value using the given generator.
     * Optionally log a message, so that when a test fails, you'd know the value was generated.
     * The message is a Java format string, so you can use it to include the generated value, e.g.
     * {@code String s = generateValue(stringsOf(asciiLetters(), "Generated %s")}.<p/>
     * If you don't want to generate message, or would like to show the generated value in a custom way, pass {@code null}.
     * You can use {@link #logMessage} later to still leave a trace of this value generation in the log.
     */
    <T> T generateValue(@NotNull Generator<T> generator, @Nullable String logMessage);

    /** Executes a sequence (of length with the given distribution) of random nested commands (produced by the given generator) */
    void executeCommands(IntDistribution count, Generator<? extends ImperativeCommand> cmdGen);

    /** Executes a non-empty sequence of random nested commands (produced by the given generator) */    
    void executeCommands(Generator<? extends ImperativeCommand> cmdGen);
  }

  /** 
   * @return a generator that runs the given command and returns a {@link Scenario} containing the execution log and result.
   * @param command a supplier for a top-level command. This supplier should not have any side effects. 
   * @see #checkScenarios
   */
  static Generator<Scenario> scenarios(@NotNull Supplier<ImperativeCommand> command) {
    return Generator.from(data -> new ScenarioImpl(command.get(), data));
  }

  /**
   * Performs a check that the scenarios generated by the given command are successful. Default {@link PropertyChecker} settings are used.
   * @param command a supplier for a top-level command. This supplier should not have any side effects. 
   */
  static void checkScenarios(@NotNull Supplier<ImperativeCommand> command) {
    PropertyChecker.forAll(scenarios(command)).shouldHold(Scenario::ensureSuccessful);
  }

  /**
   * An analog of {@link PropertyChecker#rechecking} in the imperative command world.
   * Performs a check that the scenario generated by the given command with given seed/sizeHint parameter is successful.
   * @param command a supplier for a top-level command. This supplier should not have any side effects. 
   */
  static void checkScenario(long seed, int sizeHint, @NotNull Supplier<ImperativeCommand> command) {
    PropertyChecker.forAll(scenarios(command)).rechecking(seed, sizeHint).shouldHold(Scenario::ensureSuccessful);
  }

}

