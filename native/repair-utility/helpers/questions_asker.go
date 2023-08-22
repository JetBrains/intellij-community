package helpers

import (
	"bufio"
	"errors"
	"os"
	"repair/logger"
	"strings"
)

var YesToAll bool
var NoToAll bool

func AskQuestionWithOptions(question string, options []string) (answer string) {
	if YesToAll {
		return "y"
	}
	if NoToAll {
		return "n"
	}
	return ForceAskQuestionWithOptions(question, options)
}
func ForceAskQuestionWithOptions(question string, options []string) (answer string) {
	var optionsList string
	for i, option := range options {
		if i == len(options)-1 {
			optionsList = optionsList + option
		} else {
			optionsList = optionsList + option + "/"
		}
	}
	logger.ConsoleLogger.Println(question + " [" + optionsList + "]")
	answer, err := ReadUserInput()
	logger.ExitWithExceptionOnError(err)
	answer = strings.ToLower(strings.TrimSpace(answer))
	if asnwerFitsOptions(answer, options) {
		return answer
	} else {
		logger.ConsoleLogger.Println("There is no such option. Let me ask again:")
		answer = ForceAskQuestionWithOptions(question, options)
		return answer
	}
}

func asnwerFitsOptions(answer string, options []string) bool {
	for _, option := range options {
		if strings.ToLower(answer) == strings.ToLower(option) {
			return true
		}
	}
	return false
}

func ReadUserInput() (input string, err error) {
	stdin := bufio.NewReader(os.Stdin)
	input, err = stdin.ReadString('\n')
	input = strings.Replace(input, "\n", "", -1)
	return input, err
}
func AskUserToDownloadFreshInstallation() {
	baseFileName := GetIdeBasefileName(GetIdeaBinaryToWrokWith())
	logger.ExitOnError(errors.New("Please download fresh copy of the installation from https://www.jetbrains.com/" + baseFileName + "/download/"))
}
func AskUserAndRemoveFiles(filesToRemove []string) {
	question := "Remove the mentioned files?"
	answers := []string{"y", "n"}
	answer := AskQuestionWithOptions(question, answers)
	if answer == "n" {
		return
	}
	for _, file := range filesToRemove {
		if _, err := os.Stat(GetIdeIdePackageByBinary(GetIdeaBinaryToWrokWith()) + "/" + file); err == nil {
			file = GetIdeIdePackageByBinary(GetIdeaBinaryToWrokWith()) + "/" + file
		}
		if _, err := os.Stat(file); err == nil {
			if err = os.Remove(file); err != nil {
				logger.WarningLogger.Printf("Could not remove %s file", file)
			}
		} else {
			logger.WarningLogger.Printf("Could not find %s file", file)
		}
	}
}
