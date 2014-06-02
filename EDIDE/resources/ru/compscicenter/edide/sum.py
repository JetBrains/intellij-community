import sys


def get_max_sum():
    filename = получите из консоли имя файла
    f = откройте файл на запись
    data = f.readlines()
    nums = []
    for i in data:
        nums.append(int(i))
    закройте файл
    max_sum = правильно проинициализируйте значение
    cur_sum = 0
    max_i = -1
    max_j = -1
    cur_i = 0
    for i in xrange(len(nums)):
        cur_sum += nums[i]
        if cur_sum < nums[i]:
            cur_i = i
            cur_sum = nums[cur_i]
        if cur_sum > max_sum:
            max_sum = cur_sum
            max_j = i
            max_i = cur_i
    return max_i,max_j


if __name__ == "__main__":
    print get_max_sum()
