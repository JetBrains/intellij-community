package cmd

import (
	"errors"
	"github.com/spf13/cobra"
	"repair/helpers"
	"repair/logger"
)

func init() {
	rootCmd.AddCommand(runtimeCmd)
}

var runtimeCmd = &cobra.Command{
	Use:   "runtime",
	Short: "Check the runtime that starts IDE",
	Long:  "Runtime aspect finds the JDK used to start the IDE and checks if it is OK to use.",
	PreRun: func(cmd *cobra.Command, args []string) {
		logger.InfoLogger.Println("Runtime aspect started")
	},
	Run: func(cmd *cobra.Command, args []string) {
		RunJbrAspect(args)
	},
	PostRun: func(cmd *cobra.Command, args []string) {
		logger.InfoLogger.Println("Runtime aspect finished")
	},
}

var (
	errorsToBeFixed []error
	warnings        []error
)

func RunJbrAspect(args []string) {
	errorsToBeFixed, warnings = []error{}, []error{}
	helpers.RuntimeInUse = helpers.RuntimeInfo{}
	var err error
	helpers.RuntimeInUse, err = helpers.DetectRuntimeInUse(helpers.GetIdeaBinaryToWrokWith())
	logger.ExitWithExceptionOnError(err)
	logger.DebugLogger.Println(
		"Runtime info: \nbinary: " + helpers.RuntimeInUse.BinaryPath +
			"\nbinary defined at: " + helpers.RuntimeInUse.BinaryPathDefinedAt +
			"\nbinary defined as: " + helpers.RuntimeInUse.BinaryPathDefinedAs +
			"\nversion: " + helpers.RuntimeInUse.Version + " (" + helpers.RuntimeInUse.Architecture + " bit)",
	)
	helpers.CollectErrorsToBeFixed(helpers.RuntimeVersionLessThan17(helpers.RuntimeInUse), &errorsToBeFixed)
	helpers.CollectErrorsToBeFixed(helpers.RuntimeArchitectureCorrectness(), &errorsToBeFixed)
	helpers.CollectErrorsToBeFixed(helpers.RuntimeIsBundled(), &errorsToBeFixed)
	if len(warnings) > 0 {
		warn := errors.New("\n" + helpers.FormatCollectedWarnings(warnings))
		logger.WarningLogger.Println(warn)
	}
	if len(errorsToBeFixed) > 0 {
		err = errors.New("\n" + helpers.FormatCollectedErrors(errorsToBeFixed))
		logger.ErrorLogger.Println(err)
		if helpers.IdeHasBundledRuntime(helpers.GetIdeaBinaryToWrokWith()) {
			userAccepted := helpers.SuggestResettingRuntime()
			if userAccepted {
				logger.InfoLogger.Println("Reverting runtime to default")
				err = helpers.ResetRuntimeInUse(helpers.RuntimeInUse)
				logger.ExitWithExceptionOnError(err)
			}
		} else {
			helpers.SuggestToChangeIdeToOneWithBundledRuntime()
		}
	}
	if len(errorsToBeFixed) == 0 && len(warnings) == 0 {
		logger.InfoLogger.Println("Default runtime is in use. No need to take actions.")
	}
}
