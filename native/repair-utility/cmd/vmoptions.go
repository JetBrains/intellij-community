package cmd

import (
	"github.com/spf13/cobra"
	"repair/helpers"
	"repair/logger"
)

var restoreVmoptionsFileBool bool

func init() {
	rootCmd.AddCommand(vmoptionsCmd)
	vmoptionsCmd.PersistentFlags().BoolVarP(&helpers.CheckVmoptionsFileBool, "check", "c", false, "Check .vmoptions file for correctness")
	vmoptionsCmd.PersistentFlags().BoolVarP(&restoreVmoptionsFileBool, "restore", "r", false, "Reset .vmoptions file to default")
}

var vmoptionsCmd = &cobra.Command{
	Use:          "vmoptions",
	Short:        "Check for problems in .vmoptions file used to start the IDE",
	Long:         "Vmoptions aspect finds the .vmoptions file used by the IDE and analyzes its content. ",
	SilenceUsage: true,
	PreRun: func(cmd *cobra.Command, args []string) {
		logger.InfoLogger.Println("VmOptions aspect started")

	},
	Run: func(cmd *cobra.Command, args []string) {
		if restoreVmoptionsFileBool {
			logger.ExitWithExceptionOnError(helpers.RestoreVmoptionsFile())
		}
		helpers.CheckVmoptionsFile()
	},
	PostRun: func(cmd *cobra.Command, args []string) {
		logger.InfoLogger.Println("VmOptions aspect finished")

	},
}
