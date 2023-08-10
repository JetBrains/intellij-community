package cmd

import (
	"github.com/spf13/cobra"
	"repair/helpers"
	"repair/logger"
)

func init() {
	rootCmd.AddCommand(cachesCmd)
	cachesCmd.PersistentFlags().BoolVarP(&helpers.ClearCachesFlag, "clear", "c", false, "Clear caches of all projects for the IDE")
}

var cachesCmd = &cobra.Command{
	Use:   "caches",
	Short: "Invalidate caches when called with `--clear` flag",
	Long:  `Caches aspect with '--clear' flag provides an alternative for the "Invalidate caches and restart" action, which can run outside the IDE.`,
	Run: func(cmd *cobra.Command, args []string) {
		RunCachesAspect(args)
	},
	PreRun: func(cmd *cobra.Command, args []string) {
		logger.InfoLogger.Println("Caches aspect started")
	},
	PostRun: func(cmd *cobra.Command, args []string) {
		logger.InfoLogger.Println("Caches aspect finished")
	},
}

func RunCachesAspect(args []string) {
	if helpers.ClearCachesFlag {
		helpers.CurrentIde.ClearSystemDirectory()
	} else {
		logger.InfoLogger.Println("Nothing to do here")
	}
}
