from test_helper import run_common_tests, import_file


def test_value(path):
    file = import_file(path)
    if file.python == "Python":
        return "Bravo"
    return "Use slicing."


if __name__ == '__main__':
    run_common_tests('''monty_python = "Monty Python"
python = type here
print(python)
''', '''monty_python = "Monty Python"
python =
print(python)
''', "You should modify the file")

    # TODO: get filepath. Let's now assume that we pass it as the last item in command-line
    import sys
    path = sys.argv[-1]
    test_value(path)

    #TODO: check that used negative index instead of positive