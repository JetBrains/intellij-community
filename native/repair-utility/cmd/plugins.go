package cmd

import (
	"repair/helpers"
	"repair/logger"

	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(pluginsCmd)
}

var pluginsCmd = &cobra.Command{
	Use:   "plugins",
	Short: "Check if broken, or old plugins are installed",
	Long:  "Plugins aspect checks every installed plugin for versions mismatch, available updates and deprecation notices. Connection to https://plugins.jetbrains.com is required.",
	PreRun: func(cmd *cobra.Command, args []string) {
		logger.InfoLogger.Println("Plugins aspect started")
	},
	Run: func(cmd *cobra.Command, args []string) {
		RunPluginsAspect(args)
	},
	PostRun: func(cmd *cobra.Command, args []string) {
		logger.InfoLogger.Println("Plugins aspect finished")
	},
}

func RunPluginsAspect(args []string) {
	incompatiblePluginsList := helpers.CurrentIde.CustomPluginsList().FilterIncompatible()
	logger.DebugLogger.Printf("Detected plugins: \n%s", helpers.CustomPluginsList.ToString())
	if len(incompatiblePluginsList) > 0 {
		helpers.CurrentIde.AskUserAndUpdatePlugins(incompatiblePluginsList.FilterUpdatable())
		helpers.CurrentIde.AskUserAndDisablePlugins(incompatiblePluginsList.FilterDeprecated())
		helpers.CurrentIde.RefreshPluginsList()
		helpers.CurrentIde.AskUserToRunIdeAndCheckTheIssue()
	} else {
		logger.InfoLogger.Println("No suspicious plugins found")
		if isAspectPassed() && len(helpers.PluginsList.FilterEnabled()) > 0 {
			logger.InfoLogger.Println("As \"plugins\" aspect has been defined explicitly, let's disable them all.")
			disablePluginsOneByOne()
		}
	}
}

func disablePluginsOneByOne() {
	question := "Let's try to disable all custom plugins to check if the error is caused by them?"
	options := []string{"y", "n"}
	if helpers.AskQuestionWithOptions(question, options) == "y" {
		helpers.CurrentIde.DisableAllCustomPlugins(false)
		issueIsStillHere := helpers.CurrentIde.AskUserToRunIdeAndCheckTheIssue()
		if issueIsStillHere == false {
			helpers.CurrentIde.DisableAllCustomPlugins(true)
			question := "Repair utility will try to disable custom plugins one-by-one to find the exact one causing the issue, ok?"
			options := []string{"y", "n"}
			if helpers.ForceAskQuestionWithOptions(question, options) == "y" {
				for _, plugin := range helpers.PluginsList {
					helpers.CurrentIde.DisablePlugin(plugin)
					logger.InfoLogger.Println("Plugin \"" + plugin.Name + "\" has been disabled.")
					issueIsStillHere := helpers.CurrentIde.AskUserToRunIdeAndCheckTheIssue()
					if issueIsStillHere == false {
						logger.InfoLogger.Println("Plugin to blame: " + plugin.Name)
						break
					} else {
						helpers.CurrentIde.EnablePlugin(plugin)
					}
				}

			}
		} else {
			logger.ConsoleLogger.Println("That means that the problem is not caused by plugins.")
			helpers.CurrentIde.DisableAllCustomPlugins(true)
		}
	}
}
