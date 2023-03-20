package cmd

import (
	"github.com/spf13/cobra"
	"os"
	"path/filepath"
	"repair/helpers"
	"repair/logger"
)

var (
	initialArgs []string
	ideaBinary  string
	rootCmd     = &cobra.Command{
		Use:   "repair",
		Short: "repair is the utility to check IntelliJ-based IDEs",
		Long:  `repair is a simple and automated way to fix the IDE when it cannot start.`,
		RunE: func(cmd *cobra.Command, args []string) error {
			return nil
		},
	}
)

func init() {
	rootCmd.PersistentFlags().StringVar(&ideaBinary, "path", "", "path to the IDE (default is the IDE where the script is located)")
	rootCmd.PersistentFlags().BoolVarP(&helpers.YesToAll, "yes", "y", false, "Apply all the suggested fixes automatically")
	rootCmd.PersistentFlags().BoolVarP(&logger.DebugEnabled, "debug", "", false, "Enable debug")
	rootCmd.PersistentFlags().BoolVarP(&helpers.NoToAll, "no", "n", false, "Only check the installation. All the fixes will be skipped.")
	rootCmd.InitDefaultHelpCmd()
	if !isHelpPassed() && !isCompareHashesPassed() {
		cobra.OnInitialize(initPath)
	}
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		logger.ConsoleLogger.Println(err)
		os.Exit(1)
	}
	if !isAspectPassed() && !isHelpPassed() {
		logger.InfoLogger.Println("Starting repair utility for all aspects")
		runAllAspects()
		logger.InfoLogger.Println("Repair utility finished execution.")
		if answer := helpers.ForceAskQuestionWithOptions("Is the error still here?", []string{"y", "n"}); answer == "y" {
			resetIdeSettingsAndDisableCustomPlugins()
		}
	}
}

func initPath() {
	var err error
	if ideaBinary != "" {
		passedBinary := ideaBinary
		ideaBinary, err = helpers.DetectInstallationByInnerPath(ideaBinary, true)
		if err != nil {
			logger.ConsoleLogger.Fatal("You passed \"--path\" argument with \"" + passedBinary + "\" value, but there is no IDE binary here.")
		}
	} else if possibleIdeaBinary, _ := helpers.DetectInstallationByInnerPath(getExecutablePath(), true); len(possibleIdeaBinary) > 0 {
		ideaBinary = possibleIdeaBinary
		helpers.CurrentIde.MarkRepairAsBundled()
	} else {
		ideaBinary, err = helpers.SelectIdeaBinary()
		logger.ExitWithExceptionOnError(err)
	}
	helpers.CurrentIde.CheckIfPathIsAllowed(ideaBinary)
	helpers.CurrentIde.SetBinaryToWrokWith(ideaBinary)
}

func getExecutablePath() string {
	executable, err := os.Executable()
	if err != nil {
		logger.ErrorLogger.Fatal("Could not get executable path")
	}
	return filepath.Dir(executable)
}

// returns true if user called repair command with help argument
func isHelpPassed() bool {
	for _, argument := range os.Args {
		if argument == "help" || argument == "--help" {
			return true
		}
	}
	return false
}
func isCompareHashesPassed() bool {
	for _, argument := range os.Args {
		if argument == "compare" || argument == "--compare" {
			return true
		}
	}
	return false
}

// returns true if user called repair command with an aspect
func isAspectPassed() bool {
	if len(initialArgs) == 0 {
		initialArgs = os.Args
	}
	for _, argument := range initialArgs {
		for _, aspect := range rootCmd.Commands() {
			if aspect.Name() == argument {
				return true
			}
		}
	}
	return false
}
func runAllAspects() {
	for _, command := range rootCmd.Commands() {
		if command.Name() != "help" && command.Name() != "completion" {
			os.Args = append([]string{os.Args[0], command.Name()})
			_, _ = command.ExecuteC()
		}
	}
}
func resetIdeSettingsAndDisableCustomPlugins() {
	pluginsNotice := ""
	if len(helpers.CurrentIde.CustomPluginsList().FilterEnabled()) > 0 {
		pluginsNotice = " and disable custom plugins"
	}
	question := "Let's try to restore default settings" + pluginsNotice + " as a last troubleshooting step?"
	answer := []string{"y", "n"}
	if helpers.AskQuestionWithOptions(question, answer) == "y" {
		if len(pluginsNotice) > 0 {
			helpers.CurrentIde.DisableAllCustomPlugins(false)
		}
		logger.InfoLogger.Printf("Restoring default settings...")
		helpers.CurrentIde.RemoveConfigurationDirectory()
	}
}
